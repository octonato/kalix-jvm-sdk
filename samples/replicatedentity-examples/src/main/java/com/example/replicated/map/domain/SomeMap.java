package com.example.replicated.map.domain;

import com.akkaserverless.javasdk.replicatedentity.ReplicatedCounter;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedData;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedDataFactory;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedEntityContext;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedMap;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedRegister;
import com.akkaserverless.javasdk.replicatedentity.ReplicatedSet;
import com.example.replicated.map.SomeMapApi;
import com.google.protobuf.Empty;

import java.util.stream.Collectors;

public class SomeMap extends AbstractSomeMap {
  @SuppressWarnings("unused")
  private final String entityId;

  private static final SomeMapDomain.SomeKey FOO_KEY =
      SomeMapDomain.SomeKey.newBuilder().setKey("foo").build();

  private static final SomeMapDomain.SomeKey BAR_KEY =
      SomeMapDomain.SomeKey.newBuilder().setKey("bar").build();

  private static final SomeMapDomain.SomeKey BAZ_KEY =
      SomeMapDomain.SomeKey.newBuilder().setKey("baz").build();

  public SomeMap(ReplicatedEntityContext context) {
    this.entityId = context.entityId();
  }

  // tag::update[]
  @Override
  public Effect<Empty> increaseFoo(
      ReplicatedMap<SomeMapDomain.SomeKey, ReplicatedData> map,
      SomeMapApi.IncreaseFooValue command) {
    ReplicatedCounter foo = // <1>
        (ReplicatedCounter) map.getOrCreate(FOO_KEY, ReplicatedDataFactory::newCounter);
    foo.increment(command.getValue()); // <2>
    return effects()
        .update(map) // <3>
        .thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> decreaseFoo(
      ReplicatedMap<SomeMapDomain.SomeKey, ReplicatedData> map,
      SomeMapApi.DecreaseFooValue command) {
    ReplicatedCounter foo = // <1>
        (ReplicatedCounter) map.getOrCreate(FOO_KEY, ReplicatedDataFactory::newCounter);
    foo.decrement(command.getValue()); // <2>
    return effects()
        .update(map) // <3>
        .thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> setBar(
      ReplicatedMap<SomeMapDomain.SomeKey, ReplicatedData> map, SomeMapApi.SetBarValue command) {
    @SuppressWarnings("unchecked")
    ReplicatedRegister<String> bar = // <1>
        (ReplicatedRegister<String>) map.getOrCreate(BAR_KEY, factory -> factory.newRegister(""));
    bar.set(command.getValue()); // <2>
    return effects()
        .update(map) // <3>
        .thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> addBaz(
      ReplicatedMap<SomeMapDomain.SomeKey, ReplicatedData> map, SomeMapApi.AddBazValue command) {
    @SuppressWarnings("unchecked")
    ReplicatedSet<String> baz = // <1>
        (ReplicatedSet<String>) map.getOrCreate(BAZ_KEY, ReplicatedDataFactory::newReplicatedSet);
    baz.add(command.getValue()); // <2>
    return effects()
        .update(map) // <3>
        .thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> removeBaz(
      ReplicatedMap<SomeMapDomain.SomeKey, ReplicatedData> map, SomeMapApi.RemoveBazValue command) {
    @SuppressWarnings("unchecked")
    ReplicatedSet<String> baz = // <1>
        (ReplicatedSet<String>) map.getOrCreate(BAZ_KEY, ReplicatedDataFactory::newReplicatedSet);
    baz.remove(command.getValue()); // <2>
    return effects()
        .update(map) // <3>
        .thenReply(Empty.getDefaultInstance());
  }
  // end::update[]

  // tag::get[]
  @Override
  public Effect<SomeMapApi.CurrentValues> get(
      ReplicatedMap<SomeMapDomain.SomeKey, ReplicatedData> map, SomeMapApi.GetValues command) {
    ReplicatedCounter foo = // <1>
        (ReplicatedCounter) map.getOrCreate(FOO_KEY, ReplicatedDataFactory::newCounter);
    @SuppressWarnings("unchecked")
    ReplicatedRegister<String> bar = // <1>
        (ReplicatedRegister<String>) map.getOrCreate(BAR_KEY, factory -> factory.newRegister(""));
    @SuppressWarnings("unchecked")
    ReplicatedSet<String> baz = // <1>
        (ReplicatedSet<String>) map.getOrCreate(BAZ_KEY, ReplicatedDataFactory::newReplicatedSet);
    SomeMapApi.CurrentValues currentValues =
        SomeMapApi.CurrentValues.newBuilder()
            .setFoo(foo.getValue())
            .setBar(bar.get())
            .addAllBaz(baz.stream().sorted().collect(Collectors.toList()))
            .build();
    return effects().reply(currentValues);
  }
  // end::get[]
}
