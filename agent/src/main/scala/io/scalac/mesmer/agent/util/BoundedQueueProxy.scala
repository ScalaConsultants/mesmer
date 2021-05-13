//package io.scalac.mesmer.agent.util
//
//import java.util
//import java.util.concurrent.{ BlockingQueue, TimeUnit }
//
//final class BoundedQueueProxy[E](underlying: BlockingQueue[E]) extends BlockingQueue[E] {
//  private var lastEnqueueResult: Boolean = true
//
//  def add(e: E): Boolean = {
//    lastEnqueueResult = underlying.add(e)
//    lastEnqueueResult
//  }
//
//  def offer(e: E): Boolean = {
//    lastEnqueueResult = underlying.offer(e)
//    lastEnqueueResult
//  }
//
//  def put(e: E): Unit = underlying.put(e)
//
//  def offer(e: E, timeout: Long, unit: TimeUnit): Boolean = {
//    lastEnqueueResult = underlying.offer(e, timeout, unit)
//    lastEnqueueResult
//  }
//
//  def take(): E = underlying.take()
//
//  def poll(timeout: Long, unit: TimeUnit): E = underlying.poll(timeout, unit)
//
//  def remainingCapacity(): Int = underlying.remainingCapacity()
//
//  def remove(o: Any): Boolean = underlying.remove(o)
//
//  def contains(o: Any): Boolean = underlying.contains(o)
//
//  def drainTo(c: util.Collection[_ >: E]): Int = underlying.drainTo(c)
//
//  def drainTo(c: util.Collection[_ >: E], maxElements: Int): Int = underlying.drainTo(c, maxElements)
//
//  def remove(): E = underlying.remove()
//
//  def poll(): E = underlying.poll()
//
//  def element(): E = underlying.element()
//
//  def peek(): E = underlying.peek()
//
//  def size(): Int = underlying.size()
//
//  def isEmpty: Boolean = underlying.isEmpty
//
//  def iterator(): util.Iterator[E] = underlying.iterator()
//
//  def toArray: Array[AnyRef] = underlying.toArray()
//
//  def toArray[T](a: Array[T]): Array[T] = null
//
//  def containsAll(c: util.Collection[_]): Boolean = underlying.containsAll(c)
//
//  def addAll(c: util.Collection[_ <: E]): Boolean = underlying.addAll(c)
//
//  def removeAll(c: util.Collection[_]): Boolean = underlying.removeAll(c)
//
//  def retainAll(c: util.Collection[_]): Boolean = underlying.retainAll(c)
//
//  def clear(): Unit = underlying.clear()
//}
