# De OOP a FP

Este proyecto tiene el proposito de explicar el ejemplo planteado [aquí](https://github.com/cde1gado/from-oop-to-fp-through-a-practical-case), 
donde se pretende pasar de un diseño orientado a objeto a un diseño funcional. La idea es aprender yo también a diseñar aplicaciones de manera funcional, 
la cual tiene muchas ventajas como veremos a continuación.

Antes de comenzar, decir que el código no es propio, por lo que aquí se aporta una explicación del código y del proceso seguido, así como, 
los conceptos que se utilizan. Aún así, se aportará el código dentro de un proyecto ejecutable para cada caso.

## El problema

Necesitamos crear un servicio para una biblioteca donde poder almacenar y consultar libros y usuarios. En concreto, 
necesitamos obtener todos los libros por usuario: `getUserBookCard(email)`.

Para ello, en primer lugar, necesitamos tener almacenados los usuarios y los libros en algún tipo de base de datos. 
Para este ejemplo, vamos a usar una base de datos de tipo [H2](https://www.h2database.com/html/main.html), porque 
es rápida, embebida, es de tipo JDBC, etc. Es decir, no nos importa qué base de datos usar, así que utilizamos alguna 
que sea sencilla y fácil de usar, como H2 o SQLite.

Si tenemos una base de datos, necesitamos poder interactuar con ella. A ese código que te permite de froma "sencilla" 
acceder a una base de datos es lo que se conoce como Repositorio, el cual te abstrae de saber cómo hay que traer la información 
de la base de datos, qué driver usar, etc.

Finalmente, necesitamos escribir el servicio que haciendo uso de uno o varios repositorio te devuelve la información que se necesita, 
y como hemos dicho antes, es obtener los libros de un usuario.

Vamos a proveer ahora todo el código referente a configuración, modelo y acceso a base de datos, ya que será común 
para todos los pasos que daremos posteriormente, y donde se partira de un código puramente orientado a objeto a un 
código cada vez más funcional.

### Librerías

Las librerías necesarias en este proyecto son:

* org.scala-langscala-librar
* org.typelevel.cats-core_2.12
* org.typelevel.cats-effect_2.12
* org.scalikejdbc.scalikejdbc_2.12
* com.h2database.h2
* ch.qos.logback.logback-classic

### Configuración

En la capa de configuración vamos a definir el código necesario para acceder a una base de datos.

````scala
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
````

Tenemos en primer lugar una interfaz (`trait`) que define un método `initConfig`, que devuelve un `F[Unit]`. 

Aquí, debemos  parar un minuto. El concepto interfaz (contrato) no es necesario decir nada, pero en este caso, hay una 
cosa "rara" en la definición de la interfaz: `[F[_]]`. Bien, esto se conoce como [*Higher kinded types*](https://stackoverflow.com/questions/6246719/what-is-a-higher-kinded-type-in-scala), 
lo que resumiendo es un genérico de genérico. Lo que se intenta aquí es no atar la interfaz a un tipo de retorno concreto, 
es decir, da igual si devuelve un Option, un Future o lo que sea. No vamos a profundizar más en este y vamos a quedarnos en 
que es eso, aunque para más información hay un link previo donde se puede encontrar más y mejor información.

Si tenemos una interfaz el siguiente paso es dar una implementación de la misma, en este caso, 
vamos a crear un objeto que nos permita crear un acceso a la base de datos. En Scala, un `object` 
es una única instancia del objeto ([_singleton_](https://docs.scala-lang.org/tour/singleton-objects.html)), 
es decir, no es necesario crear una clase e instanciarlo, el propio lenguaje ya te permite definir 
y tener una única instancia definiéndolo como `object`.

En ese objeto `DBAccess` tenemos por un lado unas propiedades necesarias para conectarnos a la base de datos,
que para el ejemplo se proveen en la propia definición. Lo lógico es consumir esa información a partir de 
un fichero de configuración. Y, por otro lado, tenemos la definición del método `initConfig`. Pero, vamos 
a pararnos de nuevo en esto.

Tenemos dos definiciones del mismo método: una pura; otra impura. Cuando se habla de que algo es 
[puro](https://stackoverflow.com/questions/7750533/why-are-pure-functions-called-pure) _grosso modo_ 
viene a decir que una tiene efectos no deseados y otra no. Es una de las cosas que se pretende con 
la programación funcional, y es que esto tiene muchas ventajas ya que el código siempre se va a 
comportar como se espera y no va a ocurrir nada que no controlamos y que puede hacer fallar el sistema.
Para quien quiera más información se ha dejado otro link sobre el tema, donde se explica qué es 
una función pura y cómo conseguirlo.

Luego volveremos a esto, pero saber que simplemente va a definir nuestro enlace con la base de datos, 
sin importar cómo funciona (el código puede ser un poco dificil de entender al principio).

### Excepciones

Es normal encontrarse algunas excepciones cuando tratamos con bases de datos, y aunque intentamos 
evitarlas (porque son equivalentes a un _GOTO_ y porque obligan a otros a lidiar con el problema), 
a veces hay que utilizarlas sin más. 

Vamos a definir entonces la excepción principal aquí, y es cuando no encontramos el usuario que 
se nos ha solicitado:

````scala
case object UserNotFound extends RuntimeException(s"User not found")
````

Hemos definido un _singleton_ (a partir de ahora a este tipo de definición que hemos explicado 
previamente lo vamos a llamar así), que extiende de `RuntimeException`, con el mensaje "Usuario no
encontrado". Lo que aparece aquí nuevo es la palabra `case`, que viene a significar [un objeto inmutable](https://docs.scala-lang.org/es/tour/case-classes.html), 
es decir, un objeto que no puede cambiar. 

### Modelo

Por último, necesitamos definir las clases del modelo:

````scala
case class User(id: String, email: String)
case class Book(id: String, title: String, userId: String)
case class BookCard(user: User, books: List[Book])
````

Como hemos explicado antes, con `case` definimos objetos inmutables, y como queremos tener 
varias instancias de cada clase utilizamos `class`. Tenemos, por tanto, un usuario que tiene un 
identificador y un email (ambos de tipo _String_), un libro que tiene un identificador, un 
título y un identificador de usuario (todos de tipo _String_) y, finalmente, una tarjeta de libro 
que tiene un ususario y una lista de libros.



 

 

