package dev.restate.sdk.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.restate.sdk.core.serde.jackson.JacksonSerdes;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JacksonSerdesTest {

  public static class Person {

    private final String name;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Person(@JsonProperty("name") String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Person person = (Person) o;
      return Objects.equals(name, person.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  private static Stream<Arguments> roundtripTestCases() {
    return Stream.of(
        Arguments.of(new Person("Francesco"), JacksonSerdes.of(Person.class)),
        Arguments.of(
            List.of(new Person("Francesco"), new Person("Till")),
            JacksonSerdes.of(new TypeReference<List<Person>>() {})),
        Arguments.of(
            Set.of(new Person("Francesco"), new Person("Till")),
            JacksonSerdes.of(new TypeReference<Set<Person>>() {})));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roundtripTestCases")
  <T> void roundtrip(T value, Serde<T> serde) {
    assertThat(serde.deserialize(serde.serialize(value))).isEqualTo(value);
  }
}
