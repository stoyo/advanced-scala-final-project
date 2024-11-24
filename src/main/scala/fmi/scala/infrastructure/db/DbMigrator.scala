package fmi.scala.infrastructure.db

import cats.effect.IO
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

class DbMigrator(dbConfig: DbConfig, migrationsLocation: String) {
  private val flyway: Flyway = Flyway
    .configure()
    .dataSource(dbConfig.jdbcUrl, dbConfig.user, dbConfig.password)
    .schemas("public")
    .locations(migrationsLocation)
    .table("flyway_schema_history")
    .baselineOnMigrate(true)
    .load()

  def migrate(): IO[MigrateResult] = IO.blocking(flyway.migrate())
}
