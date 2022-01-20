package io.scalac.mesmer.agent.akka.actor;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class BoundedQueueProxy<E> implements BlockingQueue<E> {

  private final BlockingQueue<E> underlying;

  public boolean getResult() {
    return lastEnqueueResult;
  }

  public void setResult(boolean lastEnqueueResult) {
    this.lastEnqueueResult = lastEnqueueResult;
  }

  private boolean lastEnqueueResult;

  public BoundedQueueProxy(BlockingQueue<E> underlying) {
    this.underlying = underlying;
  }

  @Override
  public boolean add(E e) {
    lastEnqueueResult = underlying.add(e);
    return lastEnqueueResult;
  }

  @Override
  public boolean offer(E e) {
    lastEnqueueResult = underlying.offer(e);
    return lastEnqueueResult;
  }

  @Override
  public void put(E e) throws InterruptedException {
    underlying.put(e);
  }

  @Override
  public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
    lastEnqueueResult = underlying.offer(e, timeout, unit);
    return lastEnqueueResult;
  }

  @Override
  public E take() throws InterruptedException {
    return underlying.take();
  }

  @Override
  public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    return underlying.poll(timeout, unit);
  }

  @Override
  public int remainingCapacity() {
    return underlying.remainingCapacity();
  }

  @Override
  public boolean remove(Object o) {
    return underlying.remove(o);
  }

  @Override
  public boolean contains(Object o) {
    return underlying.contains(o);
  }

  @Override
  public int drainTo(Collection<? super E> c) {
    return underlying.drainTo(c);
  }

  @Override
  public int drainTo(Collection<? super E> c, int maxElements) {
    return underlying.drainTo(c, maxElements);
  }

  @Override
  public E remove() {
    return underlying.remove();
  }

  @Override
  public E poll() {
    return underlying.poll();
  }

  @Override
  public E element() {
    return underlying.element();
  }

  @Override
  public E peek() {
    return underlying.element();
  }

  @Override
  public int size() {
    return underlying.size();
  }

  @Override
  public boolean isEmpty() {
    return underlying.isEmpty();
  }

  @Override
  public Iterator<E> iterator() {
    return underlying.iterator();
  }

  @Override
  public Object[] toArray() {
    return underlying.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return underlying.toArray(a);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return underlying.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    return underlying.addAll(c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return underlying.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return underlying.retainAll(c);
  }

  @Override
  public void clear() {
    underlying.clear();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof BoundedQueueProxy) {
      BoundedQueueProxy<E> bqp = (BoundedQueueProxy<E>) o;
      return bqp.lastEnqueueResult == this.lastEnqueueResult && underlying.equals(o);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return underlying.hashCode();
  }
}
