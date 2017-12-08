package org.javaweb.elasticsearch.core;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.ElasticsearchException;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.ScriptedField;
import org.springframework.data.elasticsearch.core.AbstractResultMapper;
import org.springframework.data.elasticsearch.core.DefaultEntityMapper;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentEntity;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentProperty;
import org.springframework.data.mapping.context.MappingContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * 简单的elasticsearch查询结果映射类
 *
 * @author yz
 */
public class SimpleSearchResultMapper extends AbstractResultMapper {

	private MappingContext<? extends ElasticsearchPersistentEntity<?>, ElasticsearchPersistentProperty> mappingContext;

	public SimpleSearchResultMapper() {
		super(new DefaultEntityMapper());
	}

	public SimpleSearchResultMapper(MappingContext<? extends ElasticsearchPersistentEntity<?>, ElasticsearchPersistentProperty> mappingContext) {
		super(new DefaultEntityMapper());
		this.mappingContext = mappingContext;
	}

	public SimpleSearchResultMapper(EntityMapper entityMapper) {
		super(entityMapper);
	}

	public SimpleSearchResultMapper(
			MappingContext<? extends ElasticsearchPersistentEntity<?>, ElasticsearchPersistentProperty> mappingContext,
			EntityMapper entityMapper) {

		super(entityMapper);
		this.mappingContext = mappingContext;
	}

	@Override
	public <T> Page<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
		long    totalHits = response.getHits().totalHits();
		List<T> results   = new ArrayList<T>();
		setSearchHit(response, clazz, results);
		return new PageImpl<T>(results, pageable, totalHits);
	}

	/**
	 * 搜索结果和实体类映射
	 *
	 * @param response 搜索结果
	 * @param clazz    实体类
	 * @param pageNum  当前页
	 * @param pageSize 分页大小
	 * @param <T>
	 * @return
	 */
	public <T> Page<T> mapResults(SearchResponse response, Class<T> clazz, int pageNum, int pageSize) {
		long    totalHits = response.getHits().totalHits();
		List<T> results   = new ArrayList<T>();
		setSearchHit(response, clazz, results);

		return new PageImpl<T>(results, new PageRequest(pageNum, pageSize), totalHits);
	}

	private <T> void setSearchHit(SearchResponse response, Class<T> clazz, List<T> results) {
		for (SearchHit hit : response.getHits()) {
			if (hit != null) {
				T result = null;

				if (StringUtils.isNotBlank(hit.getSourceAsString())) {
					result = mapEntity(hit.getSourceAsString(), clazz);
				} else {
					result = mapEntity(hit.getFields().values(), clazz);
				}

				setPersistentEntityId(result, hit.getId(), clazz);
				populateScriptFields(result, hit);
				results.add(result);
			}
		}
	}

	private <T> void populateScriptFields(T result, SearchHit hit) {
		if (hit.getFields() != null && !hit.getFields().isEmpty() && result != null) {
			for (java.lang.reflect.Field field : result.getClass().getDeclaredFields()) {
				ScriptedField scriptedField = field.getAnnotation(ScriptedField.class);

				if (scriptedField != null) {
					String         name           = scriptedField.name().isEmpty() ? field.getName() : scriptedField.name();
					SearchHitField searchHitField = hit.getFields().get(name);

					if (searchHitField != null) {
						field.setAccessible(true);

						try {
							field.set(result, searchHitField.getValue());
						} catch (IllegalArgumentException e) {
							throw new ElasticsearchException("failed to set scripted field: " + name + " with value: "
									+ searchHitField.getValue(), e);
						} catch (IllegalAccessException e) {
							throw new ElasticsearchException("failed to access scripted field: " + name, e);
						}
					}
				}
			}
		}
	}


	private <T> T mapEntity(Collection<SearchHitField> values, Class<T> clazz) {
		return mapEntity(buildJSONFromFields(values), clazz);
	}

	private String buildJSONFromFields(Collection<SearchHitField> values) {
		JsonFactory nodeFactory = new JsonFactory();
		try {
			ByteArrayOutputStream stream    = new ByteArrayOutputStream();
			JsonGenerator         generator = nodeFactory.createGenerator(stream, JsonEncoding.UTF8);
			generator.writeStartObject();

			for (SearchHitField value : values) {
				if (value.getValues().size() > 1) {
					generator.writeArrayFieldStart(value.getName());

					for (Object val : value.getValues()) {
						generator.writeObject(val);
					}

					generator.writeEndArray();
				} else {
					generator.writeObjectField(value.getName(), value.getValue());
				}
			}

			generator.writeEndObject();
			generator.flush();
			return new String(stream.toByteArray(), Charset.forName("UTF-8"));
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public <T> T mapResult(GetResponse response, Class<T> clazz) {
		T result = mapEntity(response.getSourceAsString(), clazz);

		if (result != null) {
			setPersistentEntityId(result, response.getId(), clazz);
		}
		return result;
	}

	@Override
	public <T> LinkedList<T> mapResults(MultiGetResponse responses, Class<T> clazz) {
		LinkedList<T> list = new LinkedList<T>();

		for (MultiGetItemResponse response : responses.getResponses()) {
			if (!response.isFailed() && response.getResponse().isExists()) {
				T result = mapEntity(response.getResponse().getSourceAsString(), clazz);
				setPersistentEntityId(result, response.getResponse().getId(), clazz);
				list.add(result);
			}
		}

		return list;
	}

	/**
	 * 从elasticsearch的索引中读取_id,设置到实体类中的documentId
	 * 解决spring elasticsearch 的主键映射冲突问题,默认的索引id或documentId会被_id覆盖
	 *
	 * @param result
	 * @param id
	 * @param clazz
	 */
	private <T> void setPersistentEntityId(T result, String id, Class<T> clazz) {

		if (clazz.isAnnotationPresent(Document.class)) {

			// 直接写死了,如果要输出id必须在实体层定义一个setDocumentId成员变量
			String   setDocumentId = "setDocumentId";
			Method[] methods       = clazz.getDeclaredMethods();
			for (Method method : methods) {
				if (method.getName().equals(setDocumentId)) {
					try {
						Method setter = clazz.getDeclaredMethod(setDocumentId, String.class);
						if (setter != null) {
							setter.invoke(result, id);
						}
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			}
		}
	}
}