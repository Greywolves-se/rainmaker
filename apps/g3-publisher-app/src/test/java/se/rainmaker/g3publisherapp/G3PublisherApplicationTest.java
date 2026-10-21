package se.rainmaker.g3publisherapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class G3PublisherApplicationTest {

  @Value("${server.port}")
  private int port;

  @Test
  void startsOnItsOwnPort() {
    assertThat(port).isEqualTo(8083);
  }
}
