package compman.compsrv.config

import com.mongodb.MongoClient
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.config.AbstractMongoConfiguration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoDbFactory
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories


@Configuration
@Profile("!mongo-embed")
@EnableMongoRepositories(basePackages = ["com.compservice.data"])
class MongodbConfig : AbstractMongoConfiguration() {
    @Value("\${mongodb.host}")
    var mongohost = "localhost"

    @Value("\${mongodb.database}")
    var mongoDatabaseName = "competitionmanager"

    @Value("\${mongodb.username}")
    var mongousername = "compman"

    @Value("\${mongodb.password}")
    var mongopassword = "compManagerPassword"

    @Value("\${mongodb.admindb}")
    val mongoadminDb = "admin"

    @Value("\${mongodb.auth}")
    val mongoAuth = false


    override fun getDatabaseName(): String {
        return mongoDatabaseName
    }

    @Bean
    override fun mongoClient(): MongoClient {
        if (mongoAuth) {
            val credantials: MongoCredential = MongoCredential.createCredential(mongousername, mongoadminDb, mongopassword.toCharArray())
            return MongoClient(ServerAddress(mongohost), credantials, null)
        } else {
            return MongoClient(mongohost)
        }
    }

    @Bean @Throws(Exception::class)
    override fun mongoTemplate(): MongoTemplate {
        return MongoTemplate(SimpleMongoDbFactory(mongoClient(), mongoDatabaseName), mappingMongoConverter())
    }
}
