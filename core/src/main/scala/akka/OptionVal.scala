package akka
import akka.util.{ OptionVal => AkkaOptionVal }

object OptionVal {
  type OptionVal[+T] = AkkaOptionVal[T]

  def none[A]: OptionVal[A] = AkkaOptionVal.none[A]

  def apply[A](value: A): OptionVal[A] = AkkaOptionVal(value)

}
