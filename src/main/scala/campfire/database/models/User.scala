package campfire.database.models

import java.sql.Date

import scala.slick.driver.MySQLDriver.simple._
import spray.json._
import DefaultJsonProtocol._

/**
 * Created by GoldRatio on 7/30/14.
 */

case class User(name: String, password: String, nickName: String, avatar: String, createDate: Date, modifyDate: Date)

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def name = column[String]("name", O.PrimaryKey)
  def password = column[String]("password")
  def nickName = column[String]("nick_name")
  def avatar = column[String]("avatar")
  def createDate = column[Date]("create_date")
  def modifyDate = column[Date]("modify_date")

  def * = (name, password, nickName, avatar, createDate, modifyDate) <> (User.tupled, User.unapply _)
}

object UserDAO {

  implicit val userFormat = new RootJsonFormat[User] {
    def write(u: User) = JsObject(Map("name" -> JsString(u.name), "nickName" -> JsString(u.nickName),
    "avatar" -> JsString(u.avatar)))

    def read(value: JsValue) = value match {
      case JsArray(JsString(name) :: JsString(nickName) :: JsString(avatar) :: Nil) =>
        new User(name, "", nickName, avatar, new java.sql.Date(System.currentTimeMillis()), new java.sql.Date(System.currentTimeMillis()))
      case _ => deserializationError("user expected")
    }
  }

  val users = TableQuery[UserTable]

  def createTable(implicit s: Session) = users.ddl.create

  def dropTable = {}

  def create(user: User)(implicit s: Session) {
    users.insert(user)
  }

  def findByName(name: String)(implicit s: Session): Option[User] =
    users.where(_.name === name).firstOption

  def auth(name: String, password: String)(implicit s: Session): Option[User] = {
    users.where(_.name === name).where(_.password === password).firstOption
  }

}
