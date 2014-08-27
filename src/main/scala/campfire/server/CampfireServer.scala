package campfire.server

/**
 * Created by goldratio on 8/7/14.
 */

import java.io.{ BufferedInputStream, FileInputStream }

import akka.pattern.ask
import akka.io.IO
import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef }
import campfire.database.{ MySQLSupport, MySQLActor }
import campfire.database.MySQLActor.{ GetRoster, AuthUser }
import campfire.database.models.{User, UserDAO}
import campfire.socketio.namespace.Namespace.{OnConnect, OnEvent, OnData}
import rx.lang.scala.Observable
import rx.lang.scala.Observer
import rx.lang.scala.Subject
import campfire.socketio.{ SocketIOServerWorker, SocketIOExtension}
import campfire.socketio.namespace.{ Namespace, NamespaceExtension }
import campfire.socketio.ConnectionActive.SendMessage
import spray.http.HttpHeaders.{ `Set-Cookie`, Cookie }
import spray.http._
import spray.json._
import java.util.concurrent.{ TimeUnit, ConcurrentHashMap, ConcurrentMap }
import spray.can.Http
import spray.can.websocket.frame.Frame
import spray.json.DefaultJsonProtocol
import spray.can.server.UHttp
import akka.util.Timeout
import scala.concurrent.Future
import java.util.UUID
import spray.httpx.unmarshalling._

object CampfireServer extends App with CampfireSslConfiguration {
  import JsonResult._

  //val clientSessionCache = new ConcurrentHashMap[String, String]()
 // val userSessionCache = new ConcurrentHashMap[String, String]()

  object SocketIOServer {
    def props(resovler: ActorRef) = Props(classOf[SocketIOServer], resolver)
  }

  class SocketIOServer(resolver: ActorRef) extends Actor with ActorLogging {
    def receive = {
      // when a new connection comes in we register a SocketIOConnection actor as the per connection handler
      case Http.Connected(remoteAddress, localAddress) =>
        val serverConnection = sender()
        val conn = context.actorOf(SocketIOWorker.props(serverConnection, resolver))
        serverConnection ! Http.Register(conn)
    }
  }

  val WEB_ROOT = "/Users/goldratio/WorkspaceGroup/AkkaWorkspace/Campfire/src/main/webapp"

  object SocketIOWorker {
    def props(serverConnection: ActorRef, resolver: ActorRef) = Props(classOf[SocketIOWorker], serverConnection, resolver)
  }

  class SocketIOWorker(val serverConnection: ActorRef, val resolver: ActorRef) extends Actor with SocketIOServerWorker {

    def getSessionId(request: HttpRequest): Option[String] = {
      request.headers.find(
        head => head.isInstanceOf[Cookie]).map { session =>
          session.asInstanceOf[Cookie].cookies.find(cookie => cookie.name == "session")
            .map(session => session.content)
        }.getOrElse(None)
    }

    def sendFailed(sender: ActorRef, message: String) = {
      val result = "An error has occured: " + message
      val entity = HttpEntity(ContentType(MediaTypes.`application/json`), result)
      sender ! HttpResponse(entity = entity)
    }

    val mysqlWorker = context.actorOf(Props[MySQLActor], "mysql-worker")

    implicit val timeout = Timeout(120, TimeUnit.SECONDS)
    implicit def executionContext = context.dispatcher

//    def getCurrentUser: HttpRequest => Future[Session] = {
//      req =>
//        {
//          Future(
//            getSessionId(req).map { sessionId =>
//
//              if (clientSessionCache.containsKey(sessionId)) {
//                val user = clientSessionCache.get(sessionId)
//                Session(sessionId, user)
//              }
//              else {
//                throw  new Exception("")
//              }
//            }.getOrElse(throw  new Exception("")))
//        }
//    }

    override def sessionIdGenerator: HttpRequest => Future[String] = {
      req => {
        Future(
          getSessionId(req).map { sessionId =>
            if(SessionManager.getUserNameBySession(sessionId).isDefined)
              sessionId
            else {
              println("session not found")
              throw new Exception("The call failed!!")
            }
          }.getOrElse{
            println("session not found")
            throw new Exception("The call failed!!")
          }
        )
      }
    }

    def mysqlCall[T](message: Any) = {
      (mysqlWorker ? message).map(result =>
        result.asInstanceOf[T])
    }

    def genericLogic: Receive = {
      case HttpRequest(HttpMethods.GET, Uri.Path("/socketio.html"), _, _, _) =>
        val content = renderTextFile(WEB_ROOT + "/socketio.html")
        val entity = HttpEntity(ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`), content)
        sender() ! HttpResponse(entity = entity)

      case HttpRequest(HttpMethods.POST, Uri.Path("/session"), header, entity, protocol) =>
        val currentSender = sender()
        entity.as[FormData] match {
          case Right(formData) =>
            val userNameField = formData.field("login")
            val passwordFiled = formData.field("password")
            (userNameField, passwordFiled) match {
              case e: (Some[FormField], Some[FormField]) => {
                (e._1.rawValue, e._2.rawValue) match {
                  case e @ (Some(userName), Some(password)) =>

                    val f = mysqlCall[Option[User]](AuthUser(userName.toString, password.toString))
                    val currentSender = sender()
                    f onSuccess {
                      case user =>
                        user.fold(sendFailed(currentSender, "user not found")) { user =>
                          import UserDAO.userFormat
                          val content = buildSuccessResult(user.toJson).toString()
                          val entity = HttpEntity(ContentType(MediaTypes.`application/json`), content)
                          val sessionId = UUID.randomUUID.toString
                          SessionManager.addSession(sessionId, user.name)
                          val sessionCookie = `Set-Cookie`(HttpCookie("session", sessionId ))
                          currentSender ! HttpResponse(entity = entity, headers = List(sessionCookie))
                        }
                    }
                    f onFailure {
                      case t =>
                        val result = "An error has occured: " + t.getMessage
                        val entity = HttpEntity(ContentType(MediaTypes.`application/json`), result)
                        currentSender ! HttpResponse(entity = entity)
                    }

                  case _ =>
                    val responeEntity = HttpEntity(ContentType(MediaTypes.`application/json`), "")
                    currentSender ! HttpResponse(entity = entity)

                }
              }
              case _ =>
                val responeEntity = HttpEntity(ContentType(MediaTypes.`application/json`), "")
                currentSender ! HttpResponse(entity = entity)

            }
          case Left(formData) =>
            val responeEntity = HttpEntity(ContentType(MediaTypes.`application/json`), "")
            currentSender ! HttpResponse(entity = entity)
        }

      case HttpRequest(HttpMethods.GET, Uri.Path("/jquery-1.7.2.min.js"), _, _, _) =>
        val content = renderTextFile(WEB_ROOT + "/jquery-1.7.2.min.js")
        val entity = HttpEntity(ContentType(MediaTypes.`application/javascript`), content)
        sender() ! HttpResponse(entity = entity)

      case HttpRequest(HttpMethods.GET, Uri.Path("/socket.io.js"), _, _, _) =>
        val content = renderTextFile(WEB_ROOT + "/socket.io.js")
        val entity = HttpEntity(ContentType(MediaTypes.`application/javascript`), content)
        sender() ! HttpResponse(entity = entity)

      case request @ HttpRequest(HttpMethods.GET, Uri.Path("/roster"), headers, entity, _) =>
        val currentSender = sender()
        getSessionId(request).fold(sendFailed(currentSender, "please login")) { sessionId =>
          SessionManager.getUserNameBySession(sessionId).map { userName =>

            val f = mysqlCall[List[User]](GetRoster(userName))
            f onSuccess {
              case members =>
                import UserDAO.userFormat
                import DefaultJsonProtocol._
                val content = buildSuccessResult(members.toJson).toString()
                val entity = HttpEntity(ContentType(MediaTypes.`application/json`), content)
                currentSender ! HttpResponse(entity = entity)
            }
            f onFailure {
              case t =>
                sendFailed(currentSender, t.getMessage)
            }
            f
          }.getOrElse {

            sendFailed(currentSender, "session expire")
          }
        }

      case x: HttpRequest =>
        x.headers.foreach { header =>
          println(header.name + ":" + header.value)
        }
        log.info("Got http req uri = {}", x.uri.path.toString.split("/").toList)
        val path = x.uri.path.toString()
        val content = renderFile(WEB_ROOT + path)
        val entity = path match {
          case css: String if css.endsWith(".css") =>
            HttpEntity(ContentType(MediaTypes.`text/css`), content)
          case javascript: String if javascript.endsWith(".js") =>
            HttpEntity(ContentType(MediaTypes.`application/javascript`), content)
          case html: String if html.endsWith(".html") =>
            HttpEntity(ContentType(MediaTypes.`text/html`), content)
          case _ =>
            HttpEntity(ContentType(MediaTypes.`image/png`), content)
        }

        sender() ! HttpResponse(entity = entity)

      case x: Frame =>
    }

    def renderFile(path: String) = {
      val bis = new BufferedInputStream(new FileInputStream(path))
      Stream.continually(bis.read).takeWhile(-1 !=).map(_.toByte).toArray
    }

    def renderTextFile(path: String) = {
      val source = scala.io.Source.fromFile(path)
      val lines = source.getLines mkString "\n"
      source.close()
      lines
    }
  }

  // --- json protocals for socketio messages:
  //case class Msg(message: String)
  case class Now(time: String)
  case class Message(fromUserName: String, toUserName: String, `type`: Int, content: String, clientMsgId: Long)
  case class MessageEvent(name: String, content: Message)

  object TheJsonProtocol extends DefaultJsonProtocol {
    //implicit val msgFormat = jsonFormat1(Msg)
    implicit val nowFormat = jsonFormat1(Now)
    implicit val messageFormat = jsonFormat5(Message)
    implicit val messageEventFormat = jsonFormat2(MessageEvent)
  }
  import spray.json._
  import TheJsonProtocol._

  implicit val system = ActorSystem()
  val socketioExt = SocketIOExtension(system)
  val namespaceExt = NamespaceExtension(system)
  implicit val resolver = namespaceExt.resolver



  MySQLSupport.startMySQL()
  // MySQLSupport.insertTestData()
  implicit val timeout = Timeout(120, TimeUnit.SECONDS)
  implicit def executionContext = system.dispatcher

  val mysqlWorker = system.actorOf(Props[MySQLActor], "mysql-worker")

  def mysqlCall[T](message: Any) = {
    (mysqlWorker ? message).map(result =>
      result.asInstanceOf[T])
  }

  val observer = new Observer[OnEvent] {
    override def onNext(value: OnEvent) {
      value match {
        case event @ OnEvent("chat", args, context) =>
          try {
            val packets = spray.json.JsonParser(args).convertTo[List[Message]]
            packets.foreach { packet =>
              SessionManager.getSessionIdByName(packet.toUserName).map { sessionId =>
                val messageEvent = MessageEvent("chat", packet)
                resolver ! SendMessage(sessionId, "testendpoint", messageEvent.toJson.toString())
              }
            }
          }
          catch {
            case e =>
              e.printStackTrace()
          }
        case event @ OnEvent("contacts", args, context) =>
          try {
            SessionManager.getUserNameBySession(event.sessionId).map{userName =>
              val f = mysqlCall[List[User]](GetRoster(userName))
              f onSuccess {
                case members =>
                  import UserDAO.userFormat
                  import DefaultJsonProtocol._
                  val content = buildContactsResult(members.toJson).toString()
                  resolver ! SendMessage(event.sessionId, "testendpoint", content)
              }
              f onFailure {
                case t =>
              }
            }
          }
          catch {
            case e =>
              e.printStackTrace()
          }
        case OnEvent("time", args, context) =>
          println("observed: " + "time" + ", " + args)
        case _ =>
          println("observed: " + value)
      }
    }
  }

//  val connectObserver = new Observer[OnConnect] {
//    override def onNext(value: OnConnect) {
//      SessionManager.removeBySessionId(value.sessionId)
//    }
//  }

  val channel = Subject[OnData]()
  // there is no channel.ofType method for RxScala, why?
  channel.flatMap {
    case x: OnEvent => Observable.items(x)
    case _          => Observable.empty
  }.subscribe(observer)


//  channel.flatMap {
//    case x: OnConnect => Observable.items(x)
//    case _          => Observable.empty
//  }.subscribe(connectObserver)

  // there is no channel.ofType method for RxScala, why?
  //  channel.flatMap {
  //    case x: OnConnect => Observable.items(x)
  //    case _            => Observable.empty
  //  }.subscribe(connectObserver)

  namespaceExt.startNamespace("testendpoint")
  namespaceExt.namespace("testendpoint") ! Namespace.Subscribe(channel)

  val server = system.actorOf(SocketIOServer.props(resolver), name = "socketio-server")

  IO(UHttp) ! Http.Bind(server, "0.0.0.0", 8080)

  readLine("Hit ENTER to exit ...\n")
  system.shutdown()
  system.awaitTermination()
}
