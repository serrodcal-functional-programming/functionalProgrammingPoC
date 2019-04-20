package com.serrodcal.poc

import cats.{Id, Monad}
import scalikejdbc._
import cats.effect.Sync
import cats.implicits._

// Configs

trait DBAccess[F[_]] {

  def initConfig(): F[Unit]
}

object DBAccess {

  private val Driver = "org.h2.Driver"
  private val Url = "jdbc:h2:mem:library;INIT=runscript from 'classpath:data.sql'"
  private val User = "user"
  private val Pass = "pass"

  def impure[F[_]](implicit M: Monad[F]): DBAccess[F] = new DBAccess[F] {
    def initConfig(): F[Unit] = M.pure(Class.forName(Driver)).>>(M.pure(ConnectionPool.singleton(Url, User, Pass)))
  }

  def pure[F[_]](implicit S: Sync[F]): DBAccess[F] = new DBAccess[F] {
    def initConfig(): F[Unit] = S.delay(Class.forName(Driver)).>>(S.delay(ConnectionPool.singleton(Url, User, Pass)))
  }
}

// Exceptions

case object UserNotFound extends RuntimeException(s"User not found")

// Models

case class User(id: String, email: String)

case class Book(id: String, title: String, userId: String)

case class BookCard(user: User, books: List[Book])

object User extends SQLSyntaxSupport[User] {

  val user = User.syntax("user")

  override val tableName = "users"

  def apply(rs: WrappedResultSet) = new User(rs.string("id"), rs.string("email"))
}

object Book extends SQLSyntaxSupport[Book] {

  val book = Book.syntax("book")

  override val tableName = "books"

  def apply(rs: WrappedResultSet) = new Book(rs.string("id"), rs.string("title"), rs.string("user_id"))
}

// Application

trait BookRepository {
  def findBooksByUser(userId: String): List[Book]
}

class InMemoryBookRepository(implicit session: DBSession) extends BookRepository {

  def findBooksByUser(userId: String): List[Book] =
    withSQL {
      select.from(Book as Book.book).where.eq(Book.book.userId, userId)
    }.map(rs => Book(rs)).list().apply()
}

trait UserRepository {
  def findUserByEmail(email: String): Option[User]
}

class InMemoryUserRepository(implicit session: DBSession) extends UserRepository {

  def findUserByEmail(email: String): Option[User] =
    withSQL {
      select.from(User as User.user).where.eq(User.user.email, email)
    }.map(rs => User(rs)).single().apply()
}

class LibraryService(userRepository: UserRepository, bookRepository: BookRepository) {

  def getUserBookCard(email: String): BookCard = {
    try {
      val maybeUser = userRepository.findUserByEmail(email)
      val user = maybeUser.fold[User](throw UserNotFound)(u => u)
      val books = bookRepository.findBooksByUser(user.id)
      BookCard(user, books)
    } catch {
      case error: Exception =>
        println(s"Error when getBookCard with email $email")
        throw error
    }
  }
}

// Main - Example

object Main extends App {

  DBAccess.impure[Id].initConfig()
  implicit val session = AutoSession

  val userRepository = new InMemoryUserRepository
  val bookRepository = new InMemoryBookRepository
  val libraryService = new LibraryService(userRepository, bookRepository)

  val result: BookCard = libraryService.getUserBookCard("user@mail.com")
  print(s"result:  $result")

}
