/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// tag::main-class[]
package valueentity.shoppingcart;

import com.akkaserverless.javasdk.AkkaServerless;
import com.example.valueentity.shoppingcart.ShoppingCartApi;
import com.example.valueentity.shoppingcart.domain.ShoppingCartDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public static final AkkaServerless SERVICE =
      new AkkaServerless()
          .registerValueEntity(
              ShoppingCartEntity.class,
              ShoppingCartApi.getDescriptor().findServiceByName("ShoppingCartService"),
              ShoppingCartDomain.getDescriptor());

  public static final void main(String[] args) throws Exception {
    LOG.info("started");
    SERVICE.start().toCompletableFuture().get();
  }
}
// end::main-class[]
