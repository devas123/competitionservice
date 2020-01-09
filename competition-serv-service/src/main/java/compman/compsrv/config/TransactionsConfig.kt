package compman.compsrv.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import javax.sql.DataSource


@Configuration
class TransactionsConfig {

    @Bean(name = ["transactionManager"])
    @Primary
    fun getTransactionManager(entityManagerFactoryBean: LocalContainerEntityManagerFactoryBean, dataSource: DataSource): JpaTransactionManager {
        val tm = JpaTransactionManager()
        tm.entityManagerFactory = entityManagerFactoryBean.`object`
        tm.dataSource = dataSource
        return tm
    }
}