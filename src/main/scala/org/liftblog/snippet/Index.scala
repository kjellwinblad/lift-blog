package org.liftblog.snippet
import scala.xml._

import org.liftblog.model.{Post,User,Comment}
import net.liftweb.mapper._
import java.text.SimpleDateFormat
import scala.xml.{NodeSeq, Text}
import net.liftweb._ 
import mapper._ 
import http._ 
import SHtml._ 
import util._ 
import common._
import Helpers._
import _root_.net.liftweb.http.js.{JE,JsCmd,JsCmds,Jx,jquery}
import JsCmds._ // For implicits
import JE._
import net.liftweb.http.js.jquery.JqJsCmds._ 
import net.liftweb.textile.TextileParser

class Index {
	private val strLength = 512
	/**
	 * Renders posts list.
	 */
	def post(in: NodeSeq): NodeSeq = {
		// FIXME - Bug with malformed xml when post is cut down
		def shortenText(text: String) = text //if(text.length < strLength) text else text.substring(0, strLength)+" ..."
		Post.findAll(OrderBy(Post.date, Descending)).flatMap(post => bind("post",in, 
				"title"-> <a href={urlify(post)}>{post.title}</a>, 
				"text" -> <xml:group>{Unparsed(shortenText(post.text))}</xml:group>,
				"date" -> (new SimpleDateFormat(Const.format) format post.date.get),
				"more" -> <a href={urlify(post)} class="readmore">Read more</a> ,
				"comments" -> SHtml.link("/details", 
						()=>Index.postidVar(post.id), { 
						// get number of comments for current post and bind html link in the view
						val comments = (Comment findAll By(Comment.postid, post.id)).length
						Text("Comments(%d)".format((comments)))},  ("class","comments")),
				"edit" -> (if(User.loggedIn_?) SHtml.link("/edit", ()=>Index.postidVar(post.id), Text("Edit"), ("class","readmore"))
						   else Text(""))
				)
		)
	}
	
	implicit def string2slash(str: String) = new SlashString(str)
	case class SlashString(val str: String) {
		def /(other: String) = str + "/" + other
	}
	def urlify(post: Post) = {
		val date = post.date.is
		val year = (date.getYear+1900).toString
		val month = date.getMonth+1
		val monthStr = if(month >9) month.toString else ("0"+month)
		year / monthStr / post.title
	}
	
	/**
	 * Renders post in details.
	 * @param in
	 * @return
	 */
	def show(in: NodeSeq): NodeSeq = {
		val postTitle = S.param("title") openOr S.redirectTo("/404.html")
		
		Post.find(By(Post.title, postTitle)) match {
			case Full(post) => bind("post",in, 
				"title"->post.title, 
				"text" -> <xml:group>{Unparsed(post.text)}</xml:group>,
				"date" -> (new SimpleDateFormat(Const.format) format post.date.get))
				
			case Empty => Text("No such post")
			case Failure(_,_,_) => S.redirectTo("/404.html")
		}
	}
	
	/**
	 * Binds comments for related post.
	 */
	def comments(in: NodeSeq): NodeSeq = {
		Comment.findAll(By(Comment.postid,Index.postid)).flatMap(comment =>
			bind("comment", in, "author" -> <a href={comment.website}> {comment.author}</a>,
					"text" -> <xml:group>{Unparsed(comment.text)}</xml:group>,
					"date" -> (new SimpleDateFormat("E d MMM, HH:mm") format comment.date.get))
		) 
	}
	
	/**
	 * Creates ajax form for commenting
	 */
	def addComment(in: NodeSeq): NodeSeq = {
		var author = ""
		var text = ""
		var website = ""
		// new-comment is element on page inside 
		def onSubmit = {
				val now = new java.util.Date
				val html = TextileParser.toHtml(text, false).toString
				val c = Comment.create.author(author).text(html).postid(Index.postid).date(now).website(website)
				c.validate
				c.save
				AppendHtml("new-comment",(<p class="post-footer align-left">
				 <p style="margin-bottom: 5px; font-weight: bold;"><a href={website}> {author}</a> said...<br/></p>	
					<p>{Unparsed(html)}</p>
	    			<p>{now}</p> 					
	    		</p>))
			
		}
		
		ajaxForm(bind("comm", in, 
				"author" -> SHtml.text("", a =>author=a, ("id","comm-author")),
				"website" -> SHtml.text("", w =>website=w),
				"text" -> SHtml.textarea("", t=>text=t, ("id","comm-text")),
				"submit" -> SHtml.ajaxSubmit("Post", ()=>onSubmit, ("class","button"))), Noop)
		
	}
	
}

object Index {
	object postidVar extends RequestVar(S.param("postid").map(_.toLong) openOr 0L)
	def postid: Long = postidVar.is
}
