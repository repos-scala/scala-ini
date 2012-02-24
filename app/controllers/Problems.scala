package controllers

import play.api._
import play.api.mvc._
import models._
import models.Problem
import scala.tools.nsc._
import com.twitter.util.Eval
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api._
import play.api.mvc._
import scala.util.matching.Regex

object Problems extends Controller with Secured {
  

  val SUCCESS = "success"
  val MESSAGE = "message"
  val INVALID_SOLUTION_SYNTAX = "Error: you need to add " + PuzzleRegex.BEGIN + " and " + PuzzleRegex.END
  val MISSING_ALL_FORM_VARS = "You must supply a name, description, level and category"
  val UNKNOWN_ERROR = "An unknown error occured"
  val SUBMITTED = "submitted!"

  val EXPECTED_TRUE_GOT_FALSE = "Expected true but got false"

  

  object PuzzleRegex{
    val BEGIN = "/*<*/"
    val END = "/*>*/"
    def escape( s : String ) : String = s.replace("*", "\\*")
    val ValidPuzzle = new Regex("(.*)" + escape(BEGIN) + "(.*)"+ escape(END) +"(.*)")
  }

  object Params {
    val SOLUTION = "solution"
    val ID = "id"
    val DESCRIPTION = "description"
    val NAME = "name"
    val LEVEL = "level" 
    val CATEGORY = "category"
  }
  
  def submitPuzzlePage = IsAuthenticated { username => _ =>
    User.findByEmail(username).map{ user => 
        Ok(views.html.problems.submitPuzzle(PuzzleRegex.BEGIN,  PuzzleRegex.END))
      }.getOrElse(Forbidden)
    }
  /**
   * test authentication.
   */
  def testAuthentication = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      Ok("authenticated")
    }.getOrElse(Forbidden)
  }
 
  /**
   * Display the paginated list of computers.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param filter Filter applied on computer names
   */
  def index(page: Int = 0, orderBy: Int = 1, filter: String = "") = Action { implicit request =>

    val user : User = getUser(request)
    var solutions : Seq[UserSolution] = List()
    Logger.debug("Problems.index :: user: " + user)
    if( user != null ){
      solutions = UserSolution.findSolutionsByEmail(user.email)  
    }
    
    Ok(views.html.problems.list(
      "Scala Puzzles",
      Problem.list(page = page, orderBy = orderBy, filter = ("%"+filter+"%")),orderBy, filter, user, solutions )

    )
  }
  def show(id:String) = Action{ implicit request => 

    val userIsLoggedIn : Boolean = isLoggedIn(request)
    Logger.debug("user is logged in: " + userIsLoggedIn)
    
    Ok(views.html.problems.show("username", Problem.findById(id.toLong), getUser(request)))
  }

  def solve() = Action { implicit request => 
    

    getFormParameter2( Params.SOLUTION )  match {
      case None => Ok( toJson( buildResponse(false, ("message", "No solution provided"))))
      case Some(solution) => {
        val response = _solve(solution)
        val user : User = getUser(request)
        val isSolved = response \ SUCCESS

        if( user != null && isSolved.as[Boolean] ){
         
          getFormParameter2( Params.ID ) match {
            case None =>
            case Some(id) => {
              var success : Boolean 
                = UserSolution.create(user.email, id.toLong, solution)
            }
          }
        }

        Ok( toJson(response) )
      }
    }
  }

  
 def solveAndSubmit() = Action { implicit request =>
    val solution : String = getFormParameter( Params.SOLUTION) 
    val response = _solve(solution)
    val isSolved = response \ SUCCESS

    if( isSolved.as[Boolean] )
    {
      solution match 
      {
        case PuzzleRegex.ValidPuzzle(pre,puzzleSolution,post) => {
          

          getFormParameters(Params.DESCRIPTION, Params.NAME, Params.LEVEL, Params.CATEGORY) match {
          
            case List(Some(description), Some(name), Some(level), Some(category)) => {
              val user : User = getUser(request)
              val u : Option[User] =
                 User.findByEmail(request.session.get("email").getOrElse("ed.eustace@gmail.com"))
              val email = u.getOrElse(User()).email
              
              val newPuzzle =  NewProblem(
                    name,
                    description,
                    pre + "?" + post,
                    level,
                    category,
                    email,
                    puzzleSolution)

              Problem.insert( newPuzzle )

              Ok( toJson( buildResponse(true, (MESSAGE, SUBMITTED))))

            }
            case _ => Ok( toJson( buildResponse(false, (MESSAGE, MISSING_ALL_FORM_VARS))))
          }
        }
        case _ => Ok( toJson(buildResponse(false, (MESSAGE, INVALID_SOLUTION_SYNTAX))))
      }

    }else{
      Ok( toJson( buildResponse(false, (MESSAGE,UNKNOWN_ERROR))))
    }

  }
  private def _solve( solution : String ) : JsObject = {
  
    solution match {
      case null => buildResponse(false, ("solution", solution))
      case _ => {
        
        if( solution isEmpty )
        {
          return buildResponse(false, ("message", "solution is empty"))
        }

        Logger.debug("Problems.solve :: apply: ["+ solution+"]")
        
        var result = false
        var message = ""

        try
        {
          result = (new Eval).apply[Boolean](solution)
        }
        catch
        {
          case ex: Exception => message = ex.getMessage() 
        }
        
        if( !result )
        {
          message = EXPECTED_TRUE_GOT_FALSE
        }
        buildResponse(result, ("message", message), ("solution", solution))
      }
    }
  }

  private def buildResponse( success : Boolean, t : Tuple2[String,String]* ) : JsObject = {
    
    var list : List[Tuple2[String,JsString]]= List()
    for( tuple <- t) {
      val newTuple = (tuple._1, JsString(tuple._2))
      list = list:::List(newTuple)
    }

    JsObject(
     List("success"-> JsBoolean(success)):::list
    )
  }

  //TODO: Tidy this up
  private def getFormParameters( names : String* )( implicit request : Request[AnyContent]) : List[Option[String]] = {

	  
      val l = List.fromArray(names.toArray[String])
      l match {
    
      case List() => List()
      case _ => List(getFormParameter2(l.head)) ::: getFormParameters(l.tail.toArray[String] : _*)
    }
  }


  private def getFormParameter( name : String )(implicit request : Request[AnyContent] ) : String = {
    val map : Map[String,Seq[String]] = request.body.asFormUrlEncoded.getOrElse(Map())
    map.getOrElse(name, List[String]("null")).head
  }

  private def getFormParameter2( name : String )(implicit request : Request[AnyContent] ) : Option[String] = {
    val map : Map[String,Seq[String]] = request.body.asFormUrlEncoded.getOrElse(Map())
    val resultList : Option[Seq[String]] = map.get(name)
    resultList match {
      case None => None
      case Some(list) => Some(list.head)
    }
  }

  
}
