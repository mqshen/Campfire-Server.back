package campfire.database

/**
 * Created by GoldRatio on 7/30/14.
 */

import akka.actor.Actor
import campfire.database.models.{ RosterDAO, User, UserDAO }
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.http.DateTime

object MySQLActor {
  case class GetRoster(userName: String)
  case class AuthUser(userName: String, password: String)
}

class MySQLActor extends Actor {
  import MySQLActor._
  import UserDAO.userFormat

  implicit val DateFormat = new RootJsonFormat[java.sql.Date] {
    lazy val format = new java.text.SimpleDateFormat()
    def read(json: JsValue): java.sql.Date = new java.sql.Date(json.compactPrint.toLong)
    def write(date: java.sql.Date) = JsString(date.getTime.toString)
  }

  //jsonFormat5(User)

  def receive: Receive = {
    case GetRoster(userName: String) => {
      MySQLSupport.withSession { implicit s =>
        val users = RosterDAO.findRosterByUserName(userName)
        sender ! users
      }
    }

    case AuthUser(userName: String, password: String) => {
      MySQLSupport.withSession { implicit s =>
        UserDAO.auth(userName, password).map { user =>
          sender ! Some(user)
        }.getOrElse(sender ! None)
      }
    }

    //    case CreateTask(content: String, assignee: String) =>
    //      //sender ! TaskDAO.addTask(content, assignee)
    //
    //    case FetchTask(id: Int) =>
    //      //sender ! TaskDAO.fetchTaskById(id)
    //
    //    case ModifyTask(id: Int, content: String) =>
    //      //sender ! TaskDAO.updateTaskById(id, content)
    //
    //    case DeleteTask(id: Int) =>
    //      //sender ! TaskDAO.deleteTaskById(id)
    //
    //    case DeleteAll =>
    //      //sender ! TaskDAO.deleteAll
    //
    //    case GetCount =>
    //      //sender ! TaskDAO.numberOfTasks
    //
    //    case Populate(file: String) =>
    //      //sender ! TaskDAO.populateTable(file)
    //
    //    case GetIds =>
    //      //sender ! TaskDAO.listAllIds
    //
    //    case CreateTable =>
    //      //sender ! TaskDAO.createTable
    //
    //    case DropTable =>
    //      //sender ! TaskDAO.dropTable
  }
}