package tapir.examples

import io.circe.{Decoder, Encoder}
import tapir._
import tapir.json.circe._
import io.circe.generic.auto._

object EndpointWithCustomTypes {
  // An over-complicated, example custom type
  trait MyId {
    def id: String
  }
  class MyIdImpl(val id: String) extends MyId

  // Custom type as a path or query parameter: encoding and decoding is fully handled by tapir. We need to provide
  // a custom implicit Codec
  implicit val myIdCodec: Codec[MyId, CodecFormat.TextPlain, String] =
    Codec.stringPlainCodecUtf8.map[MyId](s => new MyIdImpl(s))(myId => myId.id)
  val endpointWithMyId: Endpoint[MyId, Unit, Unit, Nothing] = endpoint.in("find" / path[MyId])

  // Custom type mapped to json: encoding and decoding is handled by circe. The Codec is automatically derived from a
  // circe Encoder and Decoder. We also need the schema (through the SchemaFor implicit) for documentation.
  case class Person(id: MyId, name: String)

  implicit val myIdSchema: SchemaFor[MyId] = SchemaFor(Schema.SString)
  // custom circe encoders and decoders need to be in-scope as well
  implicit val myIdEncoder: Encoder[MyId] = Encoder.encodeString.contramap(_.id)
  implicit val myIdDecoder: Decoder[MyId] = Decoder.decodeString.map(s => new MyIdImpl(s))
  val endpointWithPerson: Endpoint[Unit, Unit, Person, Nothing] = endpoint.out(jsonBody[Person])
}
