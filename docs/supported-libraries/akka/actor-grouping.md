---
sidebar_position: 2
---

# Actor grouping

When the actors' hierarchy grows or there are short-living actors referenced by a unique path name, it can be impractical to give each metric a unique set of attributes. This amplifies the amount of data exported and can affect collector performance. To solve this, Mesmer allows the definition of fine-grained rules on how metrics associated with different actors can be grouped together.

There are three actor attributes grouping options:
  - **group** - the metrics, collected for all the actors matching the given path, share the `actor_path` attribute.
  - **instance** - the metrics, collected for actors matching this path, have a unique `actor_path` attribute.
  - **disabled** - the metrics, collected for all the actors matching the given path, do not get `actor_path` attribute.

By default the path grouping is disabled. You can change this by launching the Mesmer extension with `-Dio.scalac.mesmer.actor.reporting-default=group` configuration option. This will give all the actors' metrics the attribute `actor_path="/"`.

Alternatively, it is possible to override a single path grouping strategy as following
```sh
java -javaagent:path/to/opentelemetry-javaagent.jar \
  -Dotel.javaagent.extensions=path/to/mesmer-otel-extension.jar \
  -Dio.scalac.mesmer.actor.reporting-default=ignore \
  -Dio.scalac.mesmer.actor.rules."/user/**"=group \
  -Dio.scalac.mesmer.actor.rules."/system/**"=group \
  -jar your-app.jar
```
This configuration will aggregate metrics for system and user hierarchies separately, grouping them by `actor_path="/system"` and `actor_path="/user"` attributes correspondingly.

The individual rule syntax is `io.scalac.mesmer.actor.rules."<MATCHING_PATH>"=<GROUPING_OPTION>`. The matching path supports limited set of wildcards, aiding to group metrics for variable path segments.

- **/** - matches exactly one actor and therefore can be used only with the `instance` grouping option.
  For a topology of */user/my-actor* and */user/my-actor/1* and the rules
  ```
  io.scalac.mesmer.actor.reporting-default=group
  io.scalac.mesmer.actor.rules."/user/my-actor"=instance
  ```
  the metrics from */user/my-actor* will get the attribute `actor_path="/user/my-actor"` while the metrics from */user/my-actor/1* will get the attribute `actor_path="/"` and likely will be grouped with metrics from other actors.

- **/\*** - matches a single variable tailing segment.
  For a topology of */user/my-actor*, */user/my-actor/1*, */user/my-other-actor* and the rules
  ```
  io.scalac.mesmer.actor.reporting-default=ignore
  io.scalac.mesmer.actor.rules."/user/*"=instance
  ```
  the metrics from */user/my-actor* and */user/my-other-actor* will get same values for `actor_path` attribute, while */user/my-actor/1* will be ignored.

- **/\*\*** - matches all tailing segments.
  For a topology of */user/my-actor*, */user/my-actor/1*, */user/my-other-actor* and the rules
  ```
  io.scalac.mesmer.actor.reporting-default=ignore
  io.scalac.mesmer.actor.rules."/user/**"=instance
  ```
  this will produce a unique metric for each actor with root at */user*. This wildcard also can be applied using with grouping,

  ```
  io.scalac.mesmer.actor.reporting-default=ignore
  io.scalac.mesmer.actor.rules."/user/**"=group
  ```

  Resulting in all metrics from actors with root at */user* to be aggregated using common attribute `actor_path="/user"`
