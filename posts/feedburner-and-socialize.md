Поддержка Feedburner и Socialize
16-01-2012 01:05
16-01-2012 01:05
rizzo, feedburner, rfc3339

Пришлось немного повозиться с форматом ATOM для rss-фида. Оказалось, что java нативно не умеет делать timestamp'ы в формате [RFC3339](http://www.ietf.org/rfc/rfc3339.txt),
который используется в ATOM. Не хватает буквально одного символа :) пришлось немного наколхозить.

    cfg.base3339DateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    cfg.zoneDateFormatter = new SimpleDateFormat("Z")

    cfg.rfc3339Format = { date ->
        def zone = cfg.zoneDateFormatter.format(date)
        cfg.base3339DateFormatter.format(date) + zone[0..2] + ':' + zone[3..4]
    }

Еще, этот пост должен сам разлететься по всем социалкам при помощи feedburner socialize.