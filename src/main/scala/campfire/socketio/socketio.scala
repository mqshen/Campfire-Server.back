package campfire

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import campfire.session.Session
import com.typesafe.config.{Config, ConfigFactory}
import spray.can.Http
import spray.can.websocket.frame.TextFrame
import campfire.socketio.{transport, ConnectionActive}
import spray.http.HttpHeaders.Origin
import spray.http._
import scala.concurrent.duration._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Created by goldratio on 8/7/14.
 */

package object socketio {
  val SOCKET_IO = "socket.io"

  val config = ConfigFactory.load().getConfig("spray.socketio")
  val Settings = new Settings(config)
  class Settings(config: Config) {
    val supportedTransports = config.getString("server.supported-transports")
    val heartbeatInterval = config.getInt("server.heartbeat-interval")
    val HeartbeatTimeout = config.getInt("server.heartbeat-timeout")
    val CloseTimeout = config.getInt("server.close-timeout")
    val IdleTimeout = config.getInt("server.idle-timeout")
    val namespacesDispatcher = config.getString("namespaces-dispatcher")
    val namespaceDispatcher = config.getString("namespace-dispatcher")
  }

  val actorResolveTimeout = config.getInt("server.actor-selection-resolve-timeout").seconds
  val namespaceSubscribeTimeout = config.getInt("server.namespace-subscribe-timeout").seconds

  /**
   * Topic for broadcast messages. Cannot contain '.' or '/'
   */
  def topicForBroadcast(endpoint: String, room: String) = "socketio-broadcast" + { if (endpoint != "") "-" + endpoint else "" } + { if (room != "") "-" + room else "" }

  /**
   * The topic used only by namespace actor. @Note __not for connections and broadcast__.
   */
  def topicForNamespace(endpoint: String) = "socketio-namespace-" + { if (endpoint == "") "global" else endpoint }

  val topicForDisconnect = "socketio-global-disconnect"

  private[socketio] final class SoConnectingContext(
                                                     var sessionId: String,
                                                     val sessionIdGenerator: HttpRequest => Future[String],
                                                     val serverConnection: ActorRef,
                                                     val socketioWorker: ActorRef,
                                                     val resolver: ActorRef,
                                                     val log: LoggingAdapter,
                                                     implicit val ec: ExecutionContext)

  final case class HandshakeState(response: HttpResponse, sessionId: String, qurey: Uri.Query, origins: Seq[HttpOrigin])
  /**
   * For generic socket.io server
   */
  object HandshakeRequest {
    def unapply(req: HttpRequest)(implicit ctx: SoConnectingContext): Option[Boolean] = req match {
      case HttpRequest(_, uri, headers, _, _) =>
        uri.path.toString.split("/") match {
          case Array("", SOCKET_IO, protocalVersion) =>
            import ctx.ec
            ctx.sessionIdGenerator(req) onComplete {
              case Success(sessionId) =>
                ctx.resolver ! ConnectionActive.CreateSession(sessionId)

                val origins = headers.collectFirst { case Origin(xs) => xs } getOrElse (Nil)
                val originsHeaders = List(
                  HttpHeaders.`Access-Control-Allow-Origin`(SomeOrigins(origins)),
                  HttpHeaders.`Access-Control-Allow-Credentials`(true))

                val respHeaders = List(HttpHeaders.Connection("keep-alive")) ::: originsHeaders
                val respEntity = List(sessionId, Settings.HeartbeatTimeout, Settings.CloseTimeout, Settings.supportedTransports).mkString(":")
                val resp = HttpResponse(
                  status = StatusCodes.OK,
                  entity = respEntity,
                  headers = respHeaders)

                ctx.serverConnection ! Http.MessageCommand(resp)

              case Failure(ex) =>
            }

            Some(true)

          case _ => None
        }

      case _ => None
    }
  }

  final case class HandshakeContext(response: HttpResponse, sessionId: String, heartbeatTimeout: Int, closeTimeout: Int)
  /**
   * Response that socket.io client got during socket.io handshake
   */
  object HandshakeResponse {
    def unapply(resp: HttpResponse): Option[HandshakeContext] = resp match {
      case HttpResponse(StatusCodes.OK, entity, headers, _) =>
        entity.asString.split(":") match {
          case Array(sessionId, heartbeatTimeout, closeTimeout, supportedTransports, _*) if supportedTransports.split(",").map(_.trim).contains(transport.WebSocket.ID) =>
            Some(HandshakeContext(resp, sessionId, heartbeatTimeout.toInt, closeTimeout.toInt))
          case _ => None
        }

      case _ => None
    }
  }

  def wsConnecting(req: HttpRequest)(implicit ctx: SoConnectingContext): Option[Boolean] = {
    val query = req.uri.query
    val origins = req.headers.collectFirst { case Origin(xs) => xs } getOrElse (Nil)
    req.uri.path.toString.split("/") match {
      case Array("", SOCKET_IO, protocalVersion, transport.WebSocket.ID, sessionId) =>
        ctx.sessionId = sessionId
        import ctx.ec
        ctx.resolver ! ConnectionActive.Connecting(sessionId, query, origins, ctx.serverConnection, transport.WebSocket)
        Some(true)
      case _ =>
        None
    }
  }

  /**
   * Test websocket frame under socketio
   */
  object WsFrame {
    def unapply(frame: TextFrame)(implicit ctx: SoConnectingContext): Option[Boolean] = {
      import ctx.ec
      // ctx.sessionId should have been set during wsConnected
      ctx.log.debug("Got TextFrame: {}", frame.payload.utf8String)
      ctx.resolver ! ConnectionActive.OnFrame(ctx.sessionId, frame.payload)
      Some(true)
    }
  }

  /**
   * Test http get request under socketio
   */
  object HttpGet {
    def unapply(req: HttpRequest)(implicit ctx: SoConnectingContext): Option[Boolean] = req match {
      case HttpRequest(HttpMethods.GET, uri, _, _, HttpProtocols.`HTTP/1.1`) =>
        val query = req.uri.query
        val origins = req.headers.collectFirst { case Origin(xs) => xs } getOrElse (Nil)
        uri.path.toString.split("/") match {
          case Array("", SOCKET_IO, protocalVersion, transport.XhrPolling.ID, sessionId) =>
            import ctx.ec
            ctx.resolver ! ConnectionActive.Connecting(sessionId, query, origins, ctx.serverConnection, transport.XhrPolling)
            ctx.resolver ! ConnectionActive.OnGet(sessionId, ctx.serverConnection)
            Some(true)
          case _ => None
        }
      case _ => None
    }
  }

  /**
   * Test http post request under socketio
   */
  object HttpPost {
    def unapply(req: HttpRequest)(implicit ctx: SoConnectingContext): Option[Boolean] = req match {
      case HttpRequest(HttpMethods.POST, uri, _, entity, HttpProtocols.`HTTP/1.1`) =>
        val origins = req.headers.collectFirst { case Origin(xs) => xs } getOrElse (Nil)
        uri.path.toString.split("/") match {
          case Array("", SOCKET_IO, protocalVersion, transport.XhrPolling.ID, sessionId) =>
            import ctx.ec
            val payload = entity.data.toByteString
            ctx.resolver ! ConnectionActive.OnPost(sessionId, ctx.serverConnection, payload)
            Some(true)
          case _ => None
        }
      case _ => None
    }
  }

}
