/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

// tag::class[]
package shopping.cart.domain;

// end::class[]
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.google.protobuf.Empty;
import shopping.cart.api.ShoppingCartApi;

import java.util.Map;
import java.util.stream.Collectors;

/** An event sourced entity. */
// tag::class[]
public class ShoppingCartEntity extends AbstractShoppingCartEntity {
  // tag::state[]
  // tag::itemAdded[]
  @SuppressWarnings("unused")
  private final String entityId;
  // end::itemAdded[]
  // end::state[]

  // tag::constructor[]
  public ShoppingCartEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }
  // end::constructor[]
  // end::class[]

  @Override
  public ShoppingCartDomain.CartState emptyState() {
    return ShoppingCartDomain.CartState.getDefaultInstance();
  }

  // tag::itemAdded[]
  @Override
  public ShoppingCartDomain.CartState itemAdded(ShoppingCartDomain.CartState currentState, ShoppingCartDomain.ItemAdded itemAdded) {
    Map<String, ShoppingCartApi.LineItem> cart = domainCartToMap(currentState);
    ShoppingCartApi.LineItem item = cart.get(itemAdded.getItem().getProductId());
    if (item == null) {
      item = domainItemToApi(itemAdded.getItem());
    } else {
      item =
          item.toBuilder()
              .setQuantity(item.getQuantity() + itemAdded.getItem().getQuantity())
              .build();
    }
    cart.put(item.getProductId(), item);
    return mapToDomainCart(cart);
  }

  private ShoppingCartApi.LineItem domainItemToApi(ShoppingCartDomain.LineItem item) {
    return ShoppingCartApi.LineItem.newBuilder()
        .setProductId(item.getProductId())
        .setName(item.getName())
        .setQuantity(item.getQuantity())
        .build();
  }
  // end::itemAdded[]

  @Override
  public ShoppingCartDomain.CartState itemRemoved(ShoppingCartDomain.CartState currentState, ShoppingCartDomain.ItemRemoved itemRemoved) {
    Map<String, ShoppingCartApi.LineItem> cart = domainCartToMap(currentState);
    ShoppingCartApi.LineItem lineItem = cart.get(itemRemoved.getProductId());
    int newQty = lineItem.getQuantity() - itemRemoved.getQuantity();

    if (newQty > 0) {
      ShoppingCartApi.LineItem newItemLine = lineItem.toBuilder().setQuantity(newQty).build();
      cart.put(itemRemoved.getProductId(), newItemLine);
    } else {
      cart.remove(itemRemoved.getProductId());
    }
    return mapToDomainCart(cart);
  }

  @Override
  public ShoppingCartDomain.CartState checkedOut(ShoppingCartDomain.CartState currentState, ShoppingCartDomain.CheckedOut checkedOut) {
    return currentState.toBuilder()
        .setCheckedOutTimestamp(checkedOut.getCheckedOutTimestamp())
        .build();
  }

  // tag::getCart[]
  @Override
  public Effect<ShoppingCartApi.Cart> getCart(ShoppingCartDomain.CartState currentState, ShoppingCartApi.GetShoppingCart getShoppingCart) {
    return effects().reply(domainCartToApi(currentState));
  }
  // end::getCart[]

  // tag::addItem[]
  @Override
  public Effect<Empty> addItem(ShoppingCartDomain.CartState currentState, ShoppingCartApi.AddLineItem addLineItem) {
    if (currentState.getCheckedOutTimestamp() > 0) {
      return effects().error("Cannot add item to checked out cart.");
    } else if (addLineItem.getQuantity() <= 0) {
      return effects().error("Quantity for item " + addLineItem.getProductId() + " must be greater than zero.");
    }
    ShoppingCartDomain.ItemAdded itemAddedEvent =
        ShoppingCartDomain.ItemAdded.newBuilder()
            .setItem(
                ShoppingCartDomain.LineItem.newBuilder()
                    .setProductId(addLineItem.getProductId())
                    .setName(addLineItem.getName())
                    .setQuantity(addLineItem.getQuantity())
                    .build())
            .build();
    return effects().emitEvent(itemAddedEvent).thenReply(__ -> Empty.getDefaultInstance());
  }
  // end::addItem[]

  @Override
  public Effect<Empty> removeItem(ShoppingCartDomain.CartState currentState, ShoppingCartApi.RemoveLineItem removeLineItem) {
    if (currentState.getCheckedOutTimestamp() > 0) {
      return effects().error("Cannot remove item from checked out cart.");
    } else {
      Map<String, ShoppingCartApi.LineItem> cart = domainCartToMap(currentState);
      if (!cart.containsKey(removeLineItem.getProductId())) {
        return effects().error(
            "Cannot remove item " + removeLineItem.getProductId() + " because it is not in the cart.");
      } else {
        ShoppingCartApi.LineItem lineItem = cart.get(removeLineItem.getProductId());
        ShoppingCartDomain.ItemRemoved event = null;
        if ((lineItem.getQuantity() - removeLineItem.getQuantity()) > 0) {
          event =
              ShoppingCartDomain.ItemRemoved.newBuilder()
                  .setProductId(removeLineItem.getProductId())
                  .setQuantity(removeLineItem.getQuantity()) // only remove requested quantity
                  .build();
        } else {
          event =
              ShoppingCartDomain.ItemRemoved.newBuilder()
                  .setProductId(removeLineItem.getProductId())
                  .setQuantity(lineItem.getQuantity()) // remove all
                  .build();
        }
        return effects().emitEvent(event).thenReply(__ -> Empty.getDefaultInstance());
      }
    }
  }

  @Override
  public Effect<ShoppingCartApi.Cart> checkoutCart(ShoppingCartDomain.CartState currentState, ShoppingCartApi.Checkout checkout) {
    if (currentState.getCheckedOutTimestamp() > 0) {
      return effects().error("Cannot checkout an already checked out cart.");
    } else {
      return effects().emitEvent(
          ShoppingCartDomain.CheckedOut.newBuilder()
              .setCheckedOutTimestamp(System.currentTimeMillis())
              .build()).thenReply(this::domainCartToApi);
    }
  }

  private Map<String, ShoppingCartApi.LineItem> domainCartToMap(ShoppingCartDomain.CartState state) {
    return state.getItemsList().stream().collect(Collectors.toMap(ShoppingCartDomain.LineItem::getProductId, this::domainItemToApi));
  }

  private ShoppingCartApi.Cart domainCartToApi(ShoppingCartDomain.CartState cart) {
    ShoppingCartApi.Cart.Builder builder = ShoppingCartApi.Cart.newBuilder();
    if (cart.getCheckedOutTimestamp() > 0) {
      builder.setCheckedOutTimestamp(cart.getCheckedOutTimestamp());
    }
    builder.addAllItems(cart.getItemsList().stream().map(this::domainItemToApi).collect(Collectors.toList()));
    return builder.build();
  }

  private ShoppingCartDomain.CartState mapToDomainCart(Map<String, ShoppingCartApi.LineItem> cart) {
    return ShoppingCartDomain.CartState.newBuilder()
        .addAllItems(cart.values().stream().map(this::apiItemToDomain).collect(Collectors.toList()))
        .build();
  }

  private ShoppingCartDomain.LineItem apiItemToDomain(ShoppingCartApi.LineItem item) {
    return ShoppingCartDomain.LineItem.newBuilder()
        .setProductId(item.getProductId())
        .setName(item.getName())
        .setQuantity(item.getQuantity())
        .build();
  }



}
