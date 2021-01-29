package compman.compsrv.config

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.repository.RocksDBProperties
import compman.compsrv.repository.RocksDBRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RocksDBProperties::class)
class RocksDBConfiguration {
    @Bean(destroyMethod = "shutdown")
    fun rocksDBRepository(mapper: ObjectMapper, properties: RocksDBProperties) : RocksDBRepository = RocksDBRepository(mapper, properties)
}