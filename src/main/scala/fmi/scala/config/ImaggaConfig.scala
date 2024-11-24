package fmi.scala.config

case class ImaggaConfig(
  baseUri: String,
  apiKey: String,
  apiSecret: String,
  confidenceThreshold: Int
)
