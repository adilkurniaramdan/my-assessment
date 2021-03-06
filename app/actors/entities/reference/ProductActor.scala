package actors.entities.reference

import javax.inject.Inject

import actors.entities.cart.Item
import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.pattern.pipe
import exceptions.ObjectNotFoundException
import models.dto.Page
import models.entities.Price
import models.entities.reference.Product
import repositories.reference.ProductRepository

import scala.concurrent.Future.{failed, successful => future}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by adildramdan on 11/17/17.
  */
class ProductActor @Inject()(productRepository: ProductRepository)(implicit ec: ExecutionContext) extends Actor with ActorLogging{

  import ProductActor._
  def receive = LoggingReceive {
    case m: RequestPage =>
      requestPage(m.page, m.size, m.sort, m.sortBy, m.filter)
        .map(data => ResponsePage(data._1, data._2))
        .pipeTo(sender())

    case m: Create  =>
      create(m.name, m.description, m.qty, m.unitPrice)
        .map(Created)
        .pipeTo(sender())

    case m: Get     =>
      get(m.id)
        .map(GetResponse)
        .pipeTo(sender())

    case m: Update  =>
      update(m.id, m.name, m.description, m.qty, m.unitPrice)
        .map(Updated)
        .pipeTo(sender())

    case m: Delete  =>
      delete(m.id)
        .map(Deleted)
        .pipeTo(sender())

    case m: UpdateQty =>
      updateQty(m.id, m.qty)

    case m: ValidateItem     =>
      checkProductStock(m)
        .pipeTo(sender())

  }

  private def requestPage(page: Int, size: Int, sort: String, sortBy: String, filter: String) = {
    productRepository.page(page, size, sort, sortBy, filter)
  }

  private def create(name: String, description: String, qty: Int, unitPrice: Price) = {
    val product = Product(None, name, description, qty, unitPrice)
    productRepository.insert(product)
  }

  private def get(id: Long) = {
    productRepository.findById(id)
  }

  private def update(id: Long, name: String, description: String, qty: Int, unitPrice: Price) = {
    productRepository
      .findById(id)
      .flatMap {
        case Some(product)  =>
          productRepository.update(id, product.copy(name = name, description = description, qty = qty, unitPrice = unitPrice))
        case None           =>
          failed(ObjectNotFoundException(s"Product with id $id not found"))
      }
  }

  private def updateQty(id: Long, qty: Int) =
    productRepository
      .findById(id)
      .flatMap {
        case Some(product)  =>
          productRepository.update(id, product.copy(qty = product.qty + qty))
        case None           =>
          failed(ObjectNotFoundException(s"Product with id $id not found"))
      }

  private def delete(id: Long)  = {
    productRepository.delete(id)
  }

  private def checkProductStock(m: ValidateItem) = {
    def loop(items: List[Item], acc: List[Boolean]): Future[List[Boolean]] = items match {
      case h :: t   =>
        productRepository
          .findById(h.product.id.get)
          .flatMap{
            case Some(product) if product.qty >= h.qty  => loop(t, true :: acc)
            case _                                      => loop(t, false :: acc)
          }
      case Nil  =>
        future(acc)
    }
    val check = loop(m.items, Nil)
    check.map{ x =>
      if (x.forall(_ == true)) ValidateItem.Successful(m)
      else ValidateItem.Unsuccessful(m, "Not all item available")
    }
  }

}


object ProductActor {
  final val Name  = "product-actor"

  def props(productRepository: ProductRepository)(implicit ec: ExecutionContext) =
    Props(new ProductActor(productRepository))

  sealed trait Command
  case class RequestPage(page: Int, size: Int, sort: String, sortBy: String, filter: String) extends Command
  case class Create(name: String, description: String, qty: Int, unitPrice: Price) extends Command
  case class Get(id: Long) extends Command
  case class Update(id: Long, name: String, description: String, qty: Int, unitPrice: Price) extends Command
  case class Delete(id: Long) extends Command
  case class UpdateQty(id: Long, qty: Int) extends Command

  sealed trait Event
  case class ResponsePage(data: List[Product], total: Int) extends Event
  case class Created(product: Product) extends Event
  case class GetResponse(product: Option[Product]) extends Command
  case class Updated(product: Product) extends Event
  case class Deleted(success: Boolean) extends Event
  
}
