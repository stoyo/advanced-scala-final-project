package fmi.scala.infrastructure

import java.time.Instant

import fmi.scala.config.JwtConfig
import org.http4s.{AuthScheme, Credentials}
import org.http4s.headers.Authorization
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

class JwtService(jwtConfig: JwtConfig) {

  def generateToken(userId: String): String = {
    val claim = JwtClaim(
      subject = Some(userId),
      expiration = Some(Instant.now.plusSeconds(1200).getEpochSecond),
      issuedAt = Some(Instant.now.getEpochSecond)
    )

    JwtCirce.encode(claim, jwtConfig.key, JwtAlgorithm.HS256)
  }

  def getUserId(token: Authorization): Option[Int] =
    token.credentials match {
      case Credentials.Token(authScheme, token) if authScheme == AuthScheme.Bearer =>
        JwtCirce.decode(token, jwtConfig.key, Seq(JwtAlgorithm.HS256))
          .toOption
          .flatMap(jwtClaim => jwtClaim.subject)
          .flatMap(id => id.toIntOption)
      case _ => None
    }
}
