# javaweb-elasticsearch

javaweb-elasticsearch是一个封装了[Spring-Data-Elasticsearch](https://github.com/spring-projects/spring-data-elasticsearch)的elasticsearch库。因为spring-data-elasticsearch项目更新速度实在是太慢了，远不及新版的elasticsearch。

在使用spring-data-elasticsearch中很多时候我们仅仅只需要用到它的查询功能，所有这里只封装了基本的Springhe 和 elasticsearch的集成以及只要基本查询功能的ElasticsearchTemplate。因为Spring Boot还没升级内置的elasticsearch版本，所以目前暂不支持Spring Boot。

## javaweb-elasticsearch 与 Spring 集成

**添加如下pom.xml依赖**

```	
<dependency>
    <groupId>org.elasticsearch</groupId>
    <artifactId>elasticsearch</artifactId>
    <version>6.0.1</version>
</dependency>

<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>transport</artifactId>
    <version>6.0.1</version>
</dependency>

<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-elasticsearch</artifactId>
    <version>3.0.2.RELEASE</version>
</dependency>

<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-jpa</artifactId>
    <version>2.0.2.RELEASE</version>
</dependency>

<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-commons-core</artifactId>
    <version>1.4.1.RELEASE</version>
</dependency>
```
需根据实际情况选择对应的依赖版本号。

**在配置Spring配置elasticsearch集群连接和ElasticsearchTemplate**

```
<!-- 加载 elasticsearch 连接 -->
<bean id="elasticsearchConnection" class="org.javaweb.elasticsearch.core.ElasticsearchConnection"
      init-method="init">
    <property name="clusterName" value="elasticsearch"/>
    <property name="clusterHost" value="127.0.0.1"/>
    <property name="clusterPort" value="9300"/>
    <property name="transportSniff" value="true"/>
</bean>

<!-- ElasticsearchTemplate 查询模板 -->
<bean id="elasticsearchTemplate" class="org.javaweb.elasticsearch.core.ElasticsearchTemplate">
    <constructor-arg name="elasticsearchConnection" ref="elasticsearchConnection"/>
</bean>
```
`clusterName`填写集群名称,`clusterHost`填写集群主机地址(集群中的任意主机地址),`clusterPort`填写集群通信端口(注意是socket端口),`transportSniff`是否自动探测集群节点。

**使用ElasticsearchTemplate做基本的查询**

```
@Resource
private ElasticsearchTemplate elasticsearchTemplate;

public Page<Documents> search(int pageNum, int pageSize) {
	SearchRequest searchRequest = elasticsearchTemplate.startQueryBuilder(INDEX, TYPE).
			setQuery(matchAllQuery()).
			setFrom(elasticsearchTemplate.convertElasticsearchPageNumber(pageNum, pageSize)).
			setSize(pageSize).
			request();

	return elasticsearchTemplate.queryForPage(searchRequest, Documents.class);
}
```

Page对象不是spring-data-elasticsearch中的分页对象，不要搞混了。

**实体层映射**

```
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "web-aliases", type = "documents", indexStoreType = "memory", shards = 10, replicas = 0, refreshInterval = "-1")
public class Documents {

	@Id
	private String documentId;

	private String id;
	private String domain;

	@Field(type = FieldType.Nested)
	@JsonProperty("header_info")
	private Map<String, Object> headerInfo;
	
	@Field(type = Date, format = DateFormat.custom, pattern = "yyyy-MM-dd HH:mm:ss")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date mtime;
	
	......
```

为了区分id和_id，这里用了documentId替代_id。因为原版的spring-data-elasticsearch自动把这两个值混在一起了无法区分，所以这里用documentId代替。

当实体类中的成员变量和es中的名称不一致时，可以用jackson的注解绑定两者。

## 版本更新

本次更新升级了elasticsearch(6.0.1)和spring-data-elasticsearch(3.0.2.RELEASE)版本为最新版本，移除了原来对javaweb项目的依赖。