package campfire.database

import com.typesafe.config.ConfigFactory

import slick.driver.MySQLDriver.simple._
import scala.slick.jdbc.meta.MTable
import campfire.database.models.{ Roster, User, RosterDAO, UserDAO }
import javax.sql.DataSource
import org.apache.commons.dbcp2.BasicDataSource

/**
 * Created by GoldRatio on 7/30/14.
 */
object MySQLSupport {

  val dataSource: DataSource = {
    val config = ConfigFactory.load().getConfig("campfire.database")
    val ds = new BasicDataSource
    ds.setDriverClassName(config.getString("driver"))
    ds.setUsername(config.getString("userName"))
    ds.setPassword(config.getString("password"))
    ds.setMaxTotal(20)
    ds.setMaxIdle(10)
    ds.setInitialSize(10)
    ds.setValidationQuery("select 1 from dual")
    ds.setUrl(s"jdbc:mysql://${config.getString("host")}:${config.getString("port")}/${config.getString("name")}")
    ds
  }

  //  def db = Database.forURL(
  //    url    = s"jdbc:mysql://${C.pgHost}:${C.pgPort}/${C.pgDBName}?user=smartlife&password=smartlife",
  //    driver = C.pgDriver
  //  )

  val database = Database.forDataSource(dataSource)

  def withSession[T](f: Session => T): T = {
    val s = database.createSession()
    try { f(s) } finally s.close()
  }

  def startMySQL() = {
    implicit val session: Session = database.createSession()
    if (MTable.getTables("user").list.isEmpty) {
      UserDAO.createTable
    }
    if (MTable.getTables("roster").list.isEmpty) {
      RosterDAO.createTable
    }
  }

  def insertTestData() = {
    implicit val session: Session = database.createSession()
    UserDAO.create(User("test", "111111", "test", "", new java.sql.Date(System.currentTimeMillis()), new java.sql.Date(System.currentTimeMillis())))
    UserDAO.create(User("mqshen", "111111", "mqshen", "", new java.sql.Date(System.currentTimeMillis()), new java.sql.Date(System.currentTimeMillis())))
    RosterDAO.create(Roster(None, "test", "mqshen", 0, Option("mm")))
  }

}
