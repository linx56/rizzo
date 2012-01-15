Добавил highlighting к движку
07-01-2012 01:00
07-01-2012 01:00
rizzo

Еще немного кодинга на выходных :) добавил поддержку подсветки синтаксиса. Тестируем на примере быстрого запуска web-сервера Jetty на Groovy. Стоит обратить
особое внимание на подключение зависимостей при помощи [Grape](http://groovy.codehaus.org/Grape).

    @GrabResolver(name='codehaus-release-repo', root='http://repository.codehaus.org')
    @Grab(group='org.mortbay.jetty', module='jetty-embedded', version='6.1.11')

    import org.mortbay.jetty.*
    import org.mortbay.jetty.servlet.*
    import groovy.servlet.*

    Server server = new Server(1234)
    Context root = new Context(server,"/",Context.SESSIONS)
    root.setResourceBase(".")
    root.addServlet(new ServletHolder(new TemplateServlet()), "*.html")
    server.start()