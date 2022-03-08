package io.scalac.mesmer.agent.akka.newhttp;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class AkkaHttpServerHeadersGetter implements TextMapGetter<HttpRequest> {

  @Override
  public Iterable<String> keys(HttpRequest httpRequest) {
    return StreamSupport.stream(httpRequest.getHeaders().spliterator(), false)
        .map(akka.http.javadsl.model.HttpHeader::lowercaseName)
        .collect(Collectors.toList());
  }

  @Override
  public String get(HttpRequest carrier, String key) {
    Optional<akka.http.javadsl.model.HttpHeader> header = carrier.getHeader(key);
    return header.map(HttpHeader::value).orElse(null);
  }
}
