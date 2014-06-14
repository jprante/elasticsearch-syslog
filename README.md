# Elasticsearch Syslog plugin

Receive syslog requests with Elasticsearch.

## Versions

| Elasticsearch version  | Plugin      | Release date |
| ---------------------- | ----------- | -------------|
| 1.2.1                  | 1.2.1.0     | Jun 14, 2014 |

## Installation

```
./bin/plugin -install syslog -url http://xbib.org/repository/org/xbib/elasticsearch/plugin/elasticsearch-syslog/1.2.1.0/elasticsearch-syslog-1.2.1.0.zip
```

Do not forget to restart the node after installing.

## Checksum

| File                                          | SHA1                                     |
| --------------------------------------------- | -----------------------------------------|
| elasticsearch-syslog-1.2.1.0.zip              | |

## Project docs

The Maven project site is available at `Github <http://jprante.github.io/elasticsearch-syslog>`_

## Issues

All feedback is welcome! If you find issues, please post them at `Github <https://github.com/jprante/elasticsearch-syslog/issues>`_

# Example

Add something like 

    *.*    @127.0.0.1:9500

to `/etc/syslog.conf` and restart syslog daemon. Then, start Elasticsearch with syslog plugin. 
After a while, you can search logs with

    curl '0:9200/syslog*/_search?pretty'
    
and the result will look like
    
    {
      "took" : 20,
      "timed_out" : false,
      "_shards" : {
        "total" : 5,
        "successful" : 5,
        "failed" : 0
      },
      "hits" : {
        "total" : 6,
        "max_score" : 1.0,
        "hits" : [ {
          "_index" : "syslog-2014.06.14",
          "_type" : "syslog",
          "_id" : "2kwLx9NySIykCT2SatT16A",
          "_score" : 1.0,
          "_source":{"protocol":"udp","local":"/0:0:0:0:0:0:0:0:9500","facility":"DAEMON","severity":"DEBUG","timestamp":"2014-06-14T21:37:31.000Z","host":"jorgprantesmbp.joerg","message":"com.apple.metadata.mdflagwriter[523]: Done with /Users/joerg/Library/Application Support/Google/Chrome/Default/TransportSecurity\n"}
        }, {
          "_index" : "syslog-2014.06.14",
          "_type" : "syslog",
          "_id" : "GdwLzUZ6Qr2lCl2bJldvmg",
          "_score" : 1.0,
          "_source":{"protocol":"udp","local":"/0:0:0:0:0:0:0:0:9500","facility":"DAEMON","severity":"DEBUG","timestamp":"2014-06-14T21:37:48.000Z","host":"jorgprantesmbp.joerg","message":"com.apple.metadata.mdflagwriter[523]: Handle message /Users/joerg/Library/Application Support/Google/Chrome/Local State\n"}
        }, {
          "_index" : "syslog-2014.06.14",
          "_type" : "syslog",
          "_id" : "jnDE-wiXRL6JrA_D6fAD2w",
          "_score" : 1.0,
          "_source":{"protocol":"udp","local":"/0:0:0:0:0:0:0:0:9500","facility":"DAEMON","severity":"DEBUG","timestamp":"2014-06-14T21:37:48.000Z","host":"jorgprantesmbp.joerg","message":"com.apple.metadata.mdflagwriter[523]: Done with /Users/joerg/Library/Application Support/Google/Chrome/Local State\n"}
        }, {
          "_index" : "syslog-2014.06.14",
          "_type" : "syslog",
          "_id" : "iwtAP-UfQjqKgb56qMkNVQ",
          "_score" : 1.0,
          "_source":{"protocol":"udp","local":"/0:0:0:0:0:0:0:0:9500","facility":"DAEMON","severity":"DEBUG","timestamp":"2014-06-14T21:37:31.000Z","host":"jorgprantesmbp.joerg","message":"com.apple.metadata.mdflagwriter[523]: Done with /Users/joerg/Library/Application Support/Google/Chrome/Local State\n"}
        }, {
          "_index" : "syslog-2014.06.14",
          "_type" : "syslog",
          "_id" : "iBkzNugeSu-76lB1wUJKAA",
          "_score" : 1.0,
          "_source":{"protocol":"udp","local":"/0:0:0:0:0:0:0:0:9500","facility":"DAEMON","severity":"DEBUG","timestamp":"2014-06-14T21:37:31.000Z","host":"jorgprantesmbp.joerg","message":"com.apple.metadata.mdflagwriter[523]: Handle message /Users/joerg/Library/Application Support/Google/Chrome/Local State\n"}
        }, {
          "_index" : "syslog-2014.06.14",
          "_type" : "syslog",
          "_id" : "GsilgWlvQnmiYjwgCTgz8g",
          "_score" : 1.0,
          "_source":{"protocol":"udp","local":"/0:0:0:0:0:0:0:0:9500","facility":"DAEMON","severity":"DEBUG","timestamp":"2014-06-14T21:37:31.000Z","host":"jorgprantesmbp.joerg","message":"com.apple.metadata.mdflagwriter[523]: Handle message /Users/joerg/Library/Application Support/Google/Chrome/Default/TransportSecurity\n"}
        } ]
      }
    }


# License

Elasticsearch Syslog Plugin

Copyright (C) 2014 Jörg Prante

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.