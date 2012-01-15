import com.petebevin.markdown.MarkdownProcessor
import groovy.text.SimpleTemplateEngine
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import org.mortbay.jetty.Server
import org.mortbay.jetty.servlet.Context
import org.mortbay.jetty.handler.ResourceHandler
import org.mortbay.jetty.handler.HandlerList
import org.mortbay.jetty.handler.DefaultHandler
import org.mortbay.jetty.Handler
import org.mortbay.jetty.servlet.ServletHolder
import groovy.servlet.TemplateServlet

@GrabResolver(name='codehaus-release-repo', root='http://repository.codehaus.org')
@Grab('com.madgag:markdownj-core:0.4.1')
@Grab(group='org.mortbay.jetty', module='jetty-embedded', version='6.1.11')



class Post {
    String title
    String name
    Date dateCreated
    Date lastUpdated
    String summary
    String content
    List tags = []
}

class Tag {
    String title
    String name
    List posts = []
    String toString() { name }
}

CliBuilder cl = new CliBuilder(usage: 'groovy rizzo -s "source" -d "destination"')

cl.s(longOpt: 'source', args: 1, required: true, 'Location of website source')
cl.d(longOpt: 'destination', args: 1, required: true, 'Location in which to place generated website')
cl.p(longOpt: 'port', args: 1, required: false, 'Serve at port')
cl.r(longOpt: 'regenerate', args: 0, required: false, 'Regenerate pages and posts')
cl.n(longOpt: 'newpost', args: 0, required: false, 'Create new post')

def opt = cl.parse(args)

if (!opt) {
    return
}

def cfg = new ConfigSlurper().parse(new File("${opt.s}/site-config.groovy").toURL())

String[] months = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"]
DateFormatSymbols dfs = new DateFormatSymbols(new Locale("ru"));
dfs.setMonths(months);
cfg.outWithMonthFormatter = new SimpleDateFormat("d MMMM yyyy 'г.' HH:mm", dfs)
cfg.yearFormatter = new SimpleDateFormat("yyyy")
cfg.monthFormatter = new SimpleDateFormat("MM")
cfg.inFormatter = cfg.inFormatter ?: new SimpleDateFormat("dd-MM-yyyy HH:mm")
cfg.outFormatter = cfg.outFormatter ?: new SimpleDateFormat("dd.MM.yyyy")
cfg.itemIdDateFormatter = cfg.itemIdDateFormatter ?: new SimpleDateFormat("yyyy-MM-dd")
cfg.base3339DateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
cfg.zoneDateFormatter = new SimpleDateFormat("Z")

cfg.rfc3339Format = { date ->
    def zone = cfg.zoneDateFormatter.format(date)
    cfg.base3339DateFormatter.format(date) + zone[0..2] + ':' + zone[3..4]
}
cfg.createPostLink = { post ->
    cfg.site.base+'/' + cfg.createPostPath(post)
}
cfg.createPostPath = { post ->
    "${cfg.yearFormatter.format(post.dateCreated)}/${cfg.monthFormatter.format(post.dateCreated)}/${post.name}"
}
cfg.createTagLink = { tag ->
    cfg.site.base + '/tags/' + tag.name + '/'
}

def charTable = ['а':'a', 'б':'b', 'в':'v', 'г':'g', 'д':'d', 'е':'e','ё':'e', 'ж':'zh', 'з':'z', 'и':"i", 'й':'i',
        'к':'k', 'л':'l', 'м':'m', 'н':'n','о':'o', 'п': 'p', 'р':'r', 'с':'s', 'т':'t', 'у':'u', 'ф':'f', 'х':'h',
        'ц':'c', 'ч':'ch','ш':'sh', 'щ':'sh', 'ъ':'\'', 'ы':'y', 'ь':'\'', 'э':'e', 'ю':'u', 'я':'ya'];
def whiteSpaces = Pattern.compile('\\s+')

def normalize = {String str ->
    StringBuilder sb = new StringBuilder(str.toLowerCase().replaceAll(whiteSpaces, '-'))
    StringBuilder out = new StringBuilder(sb.size())
    sb.toList().each { c ->
        out.append(charTable[c] ?: c)
    }
    out.toString()
}

def destExist = new File("${opt.d}").exists()

if (!destExist){
    new File("${opt.d}").mkdirs();
}

regenerate = opt.r || !destExist

if (!new File("${opt.s}/meta.groovy").exists()) {
    new File("${opt.s}/meta.groovy").write("published = \"${inFormatter.format(new Date())}\"")
}

def metadata = new ConfigSlurper().parse(new File("${opt.s}/meta.groovy").toURL())

Date lastPublished = cfg.inFormatter.parse(metadata.published)

cfg.postFiles = new File("${opt.s}/posts/")
cfg.pageFiles = new File("${opt.s}/pages/")
cfg.baseTmpl = new File("${opt.s}/templates/base.html")
cfg.tagTmpl = new File("${opt.s}/templates/tag.html")
cfg.tagsTmpl = new File("${opt.s}/templates/tags.html")
cfg.indexEntryTmpl = new File("${opt.s}/templates/entry.html")
cfg.postTmpl = new File("${opt.s}/templates/post.html")
cfg.feedTmpl = new File("${opt.s}/templates/feed.xml");
cfg.entryTmpl = new File("${opt.s}/templates/entry.xml")
cfg.siteFeed = new File("${opt.d}/feed.xml")
cfg.templateEngine = new SimpleTemplateEngine()
cfg.mdProcessor = new MarkdownProcessor()

if (opt.n){
    System.in.withReader {
        print  'Post title: '
        def title = it.readLine()
        print  'Post name: '
        def name = it.readLine()
        if (!name || name.trim().isEmpty()){
            name = title
        }
        name = normalize(name);
        print  'Post tags: '
        def tags = it.readLine()
        def post = new File("${cfg.postFiles}/${name}.md")
        def date = cfg.inFormatter.format(new Date())
        post << "$title\n$date\n$date\n$tags\n\n"
    }
    System.exit(0);
}


def posts = []
def tags = []

//Writing pages
cfg.pageFiles.eachFileMatch(~/.*[\.html|\.md]/) { file ->
    def name = file.name[0..file.name.lastIndexOf('.') - 1]
    List pageText = file.readLines()
    def page = new Post(title: pageText[0], name: name, lastUpdated: cfg.inFormatter.parse(pageText[1].toString()))
    pageText = pageText[2..-1]
    page.content = file.name.endsWith('.md') ? cfg.mdProcessor.markdown(pageText.join("\n")) : pageText.join("\n")
    if (page.lastUpdated > lastPublished || regenerate) {
        def model = ["content": page.content, "config": cfg, "commentsEnabled" : false, "title": page.title]
        def pagePath = "${opt.d}/${name}"
        new File(pagePath).mkdirs()
        new File("$pagePath/index.html").write("${cfg.templateEngine.createTemplate(cfg.baseTmpl).make(model)}")
    }
}

//Writing posts
cfg.postFiles.eachFileMatch(~/.*[\.html|\.md]/) { file ->
    def name = file.name[0..file.name.lastIndexOf('.') - 1]
    List postText = file.readLines()
    def post = new Post(title: postText[0], name: name, dateCreated: cfg.inFormatter.parse(postText[1].toString()),
            lastUpdated: cfg.inFormatter.parse(postText[2].toString()), summary: postText[4])
    List tagList = postText[3].split(", ") as List
    tagList.each { post.tags << new Tag(title: "$it", name: normalize("$it"), posts: [post]) }
    postText = postText[5..-1]
    post.content = file.name.endsWith('.md') ? cfg.mdProcessor.markdown(postText.join("\n")) : postText.join("\n")
    posts << post

    post.tags.each { tag ->
        def currentTag = tags.find {it.name.equals(tag.name)}
        if (currentTag) {
            currentTag.posts << post
        } else {
            tags << tag
        }
    }

    def postModel = ["post": post, "config": cfg]
    if (post.lastUpdated > lastPublished || regenerate) {
        postModel = ["content": "${cfg.templateEngine.createTemplate(cfg.postTmpl).make(postModel)}", "config": cfg, "commentsEnabled" : true,
                "title": post.title]
        def postPath = "${opt.d}/${cfg.createPostPath(post)}"
        new File(postPath).mkdirs()
        new File(postPath + "/index.html").write("${cfg.templateEngine.createTemplate(cfg.baseTmpl).make(postModel)}")
    }
}

//Writing tags
new File("${opt.d}/tags/").mkdirs()
tags.each { tag ->
    tag.posts = tag.posts.sort { it.dateCreated }.reverse()
    def model = ["config": cfg, "posts" : tag.posts]
    String content = cfg.templateEngine.createTemplate(cfg.tagTmpl).make(model).toString()
    model = ["content": content, "config": cfg, "commentsEnabled" : false, "title": tag.title]
    def tagPath = "${opt.d}/tags/${tag.name}"
    new File(tagPath).mkdirs()
    new File("${tagPath}/index.html").write("${cfg.templateEngine.createTemplate(cfg.baseTmpl).make(model)}")

    def max = tag.posts.size() > 20 ? 19 : tag.posts.size() - 1
    def tagFeed = new File("${opt.d}/tags/${tag.name}/feed.xml")
    String entries = ""
    tag.posts[0..max].each { post ->
        def feedEntryModel = ["post": post, "config": cfg]
        entries += "${cfg.templateEngine.createTemplate(cfg.entryTmpl).make(feedEntryModel)}"
    }
    def tagFeedModel = ["config": cfg, "entries": entries, "feedUrl": "${cfg.createTagLink(tag)}/feed.xml"]
    tagFeed.write("${cfg.templateEngine.createTemplate(cfg.feedTmpl).make(tagFeedModel)}")
}

//Writing index.html
posts = posts.sort { it.dateCreated }.reverse()
File rootIndex = new File("${opt.d}/index.html")
String indexContent = ""
def max = posts.size() > 5 ? 4 : posts.size() - 1
posts[0..max].each { post ->
    def postModel = ["post": post, "config": cfg]
    indexContent += cfg.templateEngine.createTemplate(cfg.indexEntryTmpl).make(postModel)
}
def indexModel = ["content": indexContent, "config": cfg, "commentsEnabled" : false, "title": ""]
rootIndex.write("${cfg.templateEngine.createTemplate(cfg.baseTmpl).make(indexModel)}")

//Writing tags index
def tagsModel = ["config": cfg, "tags": tags]
def tagsContent = "${cfg.templateEngine.createTemplate(cfg.tagsTmpl).make(tagsModel)}"
tagsModel = ["config": cfg, "content": tagsContent, "commentsEnabled" : false, "title": ""]
new File("${opt.d}/tags/index.html").write("${cfg.templateEngine.createTemplate(cfg.baseTmpl).make(tagsModel)}")

//Writing feeds
max = posts.size() > 20 ? 19 : posts.size() - 1
String feedEntries = ""
posts[0..max].each { post ->
    def feedItemModel = ["post": post, "config": cfg]
    feedEntries += "${cfg.templateEngine.createTemplate(cfg.entryTmpl).make(feedItemModel)}"
}
def feedModel = ["feedUrl": "${cfg.site.url}${cfg.site.base}/feed.xml", "entries": feedEntries, "config": cfg]
cfg.siteFeed.write("${cfg.templateEngine.createTemplate(cfg.feedTmpl).make(feedModel)}")

def ant = new AntBuilder()
new File("${opt.d}/css/").mkdirs()
ant.copy(todir: "${opt.d}/css/") {
    fileset(dir: "${opt.s}/css/")
}
new File("${opt.d}/js/").mkdirs()
ant.copy(todir: "${opt.d}/js/") {
    fileset(dir: "${opt.s}/js/")
}
new File("${opt.d}/images/").mkdirs()
ant.copy(todir: "${opt.d}/images/") {
    fileset(dir: "${opt.s}/images/")
}

new File("${opt.s}/meta.groovy").delete()
new File("${opt.s}/meta.groovy").write("published = \"${cfg.inFormatter.format(new Date())}\"")

if (opt.p){
    def server = new Server(Integer.parseInt(opt.p))
    def root = new Context(server,"/",Context.SESSIONS)
    ResourceHandler resourceHandler = new ResourceHandler()
    resourceHandler.setWelcomeFiles(['index.html'] as String[])
    resourceHandler.setResourceBase("./published");
    HandlerList handlers = new HandlerList();
    handlers.setHandlers([resourceHandler, new DefaultHandler()] as Handler[]);
    server.setHandler(handlers);
    root.addServlet(new ServletHolder(new TemplateServlet()), "*.html")
    server.start()
}