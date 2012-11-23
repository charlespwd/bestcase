# Bestcase

[![Build Status](https://secure.travis-ci.org/jeandenis/bestcase.png)](http://travis-ci.org/jeandenis/bestcase)

Bestcase is an A/B and multivariate testing library for Clojure.

## Features

* Create an A/B test in **one line of code**
* Measure **multiple** independent goals per test
* See results and pick winners through a **web interface** (or programmatically)
* It's **very fast**
* Store tests and results (i) in-memory or (ii) in a [Redis](http://redis.io/) store
* Written for programmers (yes, that's a feature!)

## Installation

The easiest way to get started with bestcase is to add it to your leiningen dependencies in your project.clj file:

```clojure
[bestcase "0.1.0"]
```

You can run the tests using `lein with-profile dev midje`.  By default, the tests use the in-memory store.

## Usage

Check out the [User Guide](//github.com/jeandenis/bestcase/wiki/User-Guide) and the [Examples Page](//github.com/jeandenis/bestcase/wiki/Examples) for detailed documentation.

```clojure
(ns your-app
  (:require [bestcase :as bc]
            [bestcase.store.memory :as bcm]))

;; Use the in-memory store to keep track of tests
(bc/set-config! {:store (bcm/create-memory-store)})

...

;; Let's test how different purchase buttons colors affect conversions.
;; We have three alternatives to which users are allocated evenly:
(bc/alt :purchase-button-test
        :red-button   "/images/button1.jpg"
        :green-button "/images/button2.jpg"	
        :blue-button  "/images/button3.jpg")

...

;; Let's track conversions whenever a user purchases something
(bc/score :purchase-button-test :purchase)
```

You can see results programmatically:

```clojure
;; We use the :red-button as the control variable
(bc/results :purchase-button-test :red-button)
;; => {:test-name :purchase-button-test
;;     :test-type :ab-test
;;     :alternatives ({:alternative-name :red-button
;;                     :count 182
;;                     :control true
;;                     :goal-results ({:goal-name :purchase
;;                                     :score 35
;;                                     :z-score: 0.0})}
;;                    {:alternative-name :green-button
;;                     :count 180
;;                     :goal-results ({:goal-name :purchase
;;                                     :score 45
;;                                     :z-score: 1.3252611961151077})}
;;                    {:alternative-name :blue-button
;;                     :count 188
;;                     :goal-results ({:goal-name :purchase
;;                                     :score 61
;;                                     :z-score: 2.941015722492861})})}
```

or through your webapp by using bestcase's [Ring](//github.com/ring-clojure/ring) handler:

```clojure
(ns your-app
  (:require [bestcase.util.ring :as bcr]))

(bcr/dashboard-routes "/bestcase" {:css ["/css/custom.css"]
                                   :js ["/js/custom.js"]})
```

This returns a handler that matches the route `/bestcase` to provide you a dashboard that:
* lists all active tests;
* shows you results for those tests;
* allows you to pick a winner for any active test without having to change any code or restart your server; and
* can be customized through css and javascript if you desire it.

## Learning Bestcase

Bestcase has a lot more functionality which you can learn about in the [User Guide](//github.com/jeandenis/bestcase/wiki).  With bestcase, you can:

* end a test and choose a winner programmatically;
* put different weights on alternatives so that some are tested more often than others;
* toggle whether a user can count as a test participate or complete a goal more than once;
* change backend store through `(set-config! ...)` and `(with-config ...)`;
* exclude bots;
* force different alternatives from your browser using the query string (mainly for testing);
* pretty-print results on the command-line; and
* more.

## Documentation

* [User Guide](//github.com/jeandenis/bestcase/wiki/User-Guide)
* [Examples](//github.com/jeandenis/bestcase/wiki/Examples)
* [API Docs](http://jeandenis.github.com/bestcase/bestcase.core.html)

## Performance

The Performance Section of the [User Guide](//github.com/jeandenis/bestcase/wiki/User-Guide) contains arbitrary, non-rigorous performance non-benchmarks.

* If you are handling hundreds (100s) of requests per second, you will be just fine.
* If you are handling many thousands (1000s) of requests per second, benchmark bestcase first, but you should be fine.
* If you are handling many tens of thousands (10000s) of requests per second you aren't reading this.

If you are encountering performance bottlenecks, let me know and I'll spend some time optimizing things and implementing a hybrid backend (in-memory + Redis).

## Roadmap

* Reporting ongoing tests to analytics libraries or 3rd party services such as MixPanel
* Hybrid backend using a database + Redis as opposed to Redis alone
* Hybrid backend using an in-memory store + Redis snapshots at fixed-intervals to optimize for performance
* Multi-armed bandit tests
* Visualizations of results

Don't hesitate to email me at jeandenis at gmail.com if you have suggestions or comments.  I am friendly.

## Further Reading On A/B Testing

* [A/Bingo](http://www.bingocardcreator.com/abingo) (the inspiration), its author's [blog](http://www.kalzumeus.com/blog/), and its [greatest hits](http://www.kalzumeus.com/greatest-hits/).
* [Effective A/B Testing](http://elem.com/~btilly/effective-ab-testing/) by Ben Tilly.
* Articles by Jesse Farmer on A/B Testing: [Intro](http://20bits.com/article/an-introduction-to-ab-testing), [Statistical Analysis](http://20bits.com/article/statistical-analysis-and-ab-testing), and [Speed v. Certainty](http://20bits.com/article/speed-vs-certainty-in-ab-testing).

If you are looking to run multi-armed bandit tests in Clojure, check out [Touchstone](//github.com/ptaoussanis/touchstone). Here are a few articles on A/B testing versus multi-armed bandit testing:

* http://visualwebsiteoptimizer.com/split-testing-blog/multi-armed-bandit-algorithm/
* http://www.chrisstucchio.com/blog/2012/bandit_algorithms_vs_ab.html
* http://michaelthinks.typepad.com/blog/2012/06/the-ab-testing-and-multi-armed-bandit-kerfuffle.html

A/B versus bandit: either way, you'll be better off than not testing and that's the thing that matters most.

## Thanks

This project borrows from the [A/Bingo](http://www.bingocardcreator.com/abingo) Ruby testing framework by [Patrick McKenzie](http://www.kalzumeus.com/blog/).  Thank you for your work.

I'd also like to thank the authors of the following Clojure libraries, which are used under the hood:

* [Ring](//github.com/ring-clojure/ring) (simple webapps in Clojure)
* [Compojure](//github.com/weavejester/compojure) (routes for Ring)
* [Hiccup](//github.com/weavejester/hiccup) (library for representing html in Clojure)
* [Midje](//github.com/marick/Midje) (a fun and powerful testing framework)

## License

Copyright © 2012 Jean-Denis Greze

Like Clojure, bestcase is distributed under the Eclipse Public License.
