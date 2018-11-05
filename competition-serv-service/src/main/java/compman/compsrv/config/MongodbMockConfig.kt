package compman.compsrv.config

import com.mongodb.MongoClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.config.AbstractMongoConfiguration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoDbFactory
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@Configuration
@Profile("mongo-embed")
@EnableMongoRepositories(basePackages = ["com.compservice.data"])
class MongodbMockConfig : AbstractMongoConfiguration() {
    @Value("\${spring.data.mongodb.host}")
    var mongohost = "localhost"

    @Value("\${spring.data.mongodb.database}")
    var mongoDatabaseName = "compman"


    override fun getDatabaseName(): String {
        return mongoDatabaseName
    }

    @Bean
    override fun mongoClient(): MongoClient {
            return MongoClient(mongohost, 12345)
    }

    @Bean
    @Throws(Exception::class)
    override fun mongoTemplate(): MongoTemplate {
        return MongoTemplate(SimpleMongoDbFactory(mongoClient(), mongoDatabaseName), mappingMongoConverter())
    }
}