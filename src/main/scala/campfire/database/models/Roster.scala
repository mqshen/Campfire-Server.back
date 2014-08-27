package campfire.database.models

import scala.slick.driver.MySQLDriver.simple._
import java.sql.Date

/**
 * Created by GoldRatio on 7/31/14.
 */
case class Roster(id: Option[Long], userName: String, rosterUserName: String, sub: Int, nickName: Option[String]) {

}

class RosterTable(tag: Tag) extends Table[Roster](tag, "roster") {
  def id = column[Option[Long]]("id", O.PrimaryKey, O.AutoInc)
  def userName = column[String]("user_name")
  def rosterUserName = column[String]("roster_user_name")
  def sub = column[Int]("sub")
  def nickName = column[Option[String]]("nick_name")

  def * = (id, userName, rosterUserName, sub, nickName) <> (Roster.tupled, Roster.unapply _)
}

object RosterDAO {

  val rosters = TableQuery[RosterTable]

  def createTable(implicit s: Session) = rosters.ddl.create

  def dropTable = {}

  def create(roster: Roster)(implicit s: Session) {
    rosters.insert(roster)
  }

  def findRosterByUserName(userName: String)(implicit s: Session): List[User] = {
    (for {
      (roster, user) <- rosters innerJoin UserDAO.users on {
        case (t1, t2) =>
          t1.rosterUserName === t2.name
      }
      if roster.userName === userName
    } yield user).list
  }

}