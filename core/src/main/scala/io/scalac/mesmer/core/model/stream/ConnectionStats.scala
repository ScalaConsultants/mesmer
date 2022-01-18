package io.scalac.mesmer.core.model.stream

/**
 * All information inside [[_root_.akka.stream.impl.fusing.GraphInterpreter]] should be local to that interpreter
 * meaning that all connections in array [[_root_.akka.stream.impl.fusing.GraphInterpreter#connections]] are between
 * logics owned by same GraphInterpreter MODIFY IF THIS IS NOT TRUE!
 *
 * @param in
 *   index of inHandler owner
 * @param out
 *   index of outHandler owner
 * @param pull
 *   demand to upstream
 * @param push
 *   elements pushed to downstream
 */
final case class ConnectionStats(in: Int, out: Int, pull: Long, push: Long)
