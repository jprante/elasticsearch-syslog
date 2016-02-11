# Elasticsearch Syslog plugin

With this plugin, Elasticsearch can receive syslog messages over UDP or TCP protocol.

JSON messages are automatically recognized and parsed.

A `@cee` prefix is recognized, see

http://cee.mitre.org/language/1.0-beta1/clt.html#transport-mappings

## Parameter

You can set the following parameters:

- `syslog.host` host address for socket bind call (default not set, will use 0.0.0.0 = bind all)
- `syslog.port` port number for socket bind call (default 9500-9600 port range)
- `syslog.index` for index name (default 'syslog-'YYYY.MM.dd)
- `syslog.index_is_timewindow` if index name is a date pattern (default true)
- `syslog.type` for index type (default syslog)
- `syslog.bulk_actions` number of actions in a single bulk action (default 1000)
- `syslog.bulk_size` maximum volume of a single bulk request (default 5MB)
- `syslog.flush_interval` bulk indexing flush interval (default 5s)
- `syslog.concurrent_requests` bulk request concurrency (default 4)
- `syslog.receive_buffer_size` socket receive buffer size (default 5MB)
- `syslog.field_names` for mapping field names of the indexed syslog message
- `syslog.patterns` for matching content in the syslog messages

## Versions

| Elasticsearch version  | Plugin      | Release date |
| ---------------------- | ----------- | -------------|
| 2.2.0                  | 2.2.0.0     | Feb 11, 2016 |
| 2.1.1                  | 2.1.1.0     | Dec 27, 2015 |
| 1.4.0                  | 1.4.0.3     | Jan 21, 2014 |
| 1.4.0                  | 1.4.0.0     | Dec  4, 2014 |
| 1.2.1                  | 1.2.1.1     | Jun 15, 2014 |

## Installation for Elasticsearch 2.x

```
./bin/plugin install http://xbib.org/repository/org/xbib/elasticsearch/plugin/elasticsearch-syslog/2.2.0.0/elasticsearch-syslog-2.2.0.0.zip
```

## Installation for Elasticsearch 1.x

```
./bin/plugin -install syslog -url http://xbib.org/repository/org/xbib/elasticsearch/plugin/elasticsearch-syslog/1.4.0.3/elasticsearch-syslog-1.4.0.3.zip
```

Do not forget to restart the node after installing.

## Project docs

The Maven project site is available at [Github](http://jprante.github.io/elasticsearch-syslog)

## Issues

All feedback is welcome! If you find issues, please post them at [Github](https://github.com/jprante/elasticsearch-syslog/issues)

# Example

Add something like 

    *.*    @127.0.0.1:9500

to `/etc/syslog.conf` and restart the syslog daemon. Then, start Elasticsearch with syslog plugin. 
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

## Example: field name mapping with `syslog.field_names`

The default field names are

    protocol*   "udp" or "tcp"
    local*      local INET address
    remote*     remote INET address
    host        parsed host name
    facility    parsed facility name
    severity    parsed severity name
    timestamp   parsed timestamp (converted to Elasticsearch `dateOptionalTime` format in UTC)
    message     raw message

If you want to rename fields (the fields marked with `*` can not be changed), you can use a field name map like this:

    syslog:
        field_names:
            timestamp : "@timestamp"

This renames the field `timestamp` to `@timestamp` (e.g. for convenience with Kibana)

## Example: PHP log parsing
 
If you want to create structured logs from PHP by using the `syslog.patterns` feature, this example is for you.

Change your syslog daemon to forward all messages to Elasticsearch syslog plugin at port 9500 on same host by adding

    *.*    @127.0.0.1:9500

to `/etc/syslog.conf` and restart the syslog daemon. 

In `$ES_HOME/config/elasticsearch.yml`, define patterns to create extra fields if the syslog message matches.

    syslog:
        patterns:
            criticality: "PHP (.*?):"
            file: "in (.*?) on line"
            line: "on line (.*)$"

Then, you can test PHP errors from command line:

    php -d error_log=syslog -d log_errors=On -r 'trigger_error("Alles scheisse");'

    curl '0:9200/syslog*/_search?q=php&pretty'

    {
      "_index" : "syslog-2014.06.15",
      "_type" : "syslog",
      "_id" : "KDJqCAuMReGwAPa7i2vbJQ",
      "_score" : 1.3575592,
      "_source":{"protocol":"udp","local":"/0:0:0:0:0:0:0:0:9500","facility":"USER","severity":"NOTICE","timestamp":"2014-06-15T12:36:29.000Z","host":"jorgprantesmbp.joerg","message":"php[32105]: PHP Notice:  Alles scheisse in Command line code on line 1\n","criticality":"Notice","file":"Command line code","line":"1"}
    }


# License

Syslog Plugin for Elasticsearch

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