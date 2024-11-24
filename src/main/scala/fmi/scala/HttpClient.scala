package fmi.scala

import java.util.concurrent.Executors

import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.{EntityDecoder, Request}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.client.middleware.Logger

import scala.concurrent.ExecutionContext

object HttpClient {
  private val httpClientEc = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors))
  private val httpClient: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](httpClientEc).resource

  def request[A](req: Request[IO])(implicit d: EntityDecoder[IO, A]): IO[A] =
    httpClient.use(client => Logger(logBody = true, logHeaders = true)(client).expect[A](req))
}
