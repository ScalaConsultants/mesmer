"use strict";(self.webpackChunkdocs=self.webpackChunkdocs||[]).push([[122],{3905:function(t,e,a){a.d(e,{Zo:function(){return m},kt:function(){return g}});var r=a(7294);function n(t,e,a){return e in t?Object.defineProperty(t,e,{value:a,enumerable:!0,configurable:!0,writable:!0}):t[e]=a,t}function l(t,e){var a=Object.keys(t);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(t);e&&(r=r.filter((function(e){return Object.getOwnPropertyDescriptor(t,e).enumerable}))),a.push.apply(a,r)}return a}function u(t){for(var e=1;e<arguments.length;e++){var a=null!=arguments[e]?arguments[e]:{};e%2?l(Object(a),!0).forEach((function(e){n(t,e,a[e])})):Object.getOwnPropertyDescriptors?Object.defineProperties(t,Object.getOwnPropertyDescriptors(a)):l(Object(a)).forEach((function(e){Object.defineProperty(t,e,Object.getOwnPropertyDescriptor(a,e))}))}return t}function i(t,e){if(null==t)return{};var a,r,n=function(t,e){if(null==t)return{};var a,r,n={},l=Object.keys(t);for(r=0;r<l.length;r++)a=l[r],e.indexOf(a)>=0||(n[a]=t[a]);return n}(t,e);if(Object.getOwnPropertySymbols){var l=Object.getOwnPropertySymbols(t);for(r=0;r<l.length;r++)a=l[r],e.indexOf(a)>=0||Object.prototype.propertyIsEnumerable.call(t,a)&&(n[a]=t[a])}return n}var p=r.createContext({}),o=function(t){var e=r.useContext(p),a=e;return t&&(a="function"==typeof t?t(e):u(u({},e),t)),a},m=function(t){var e=o(t.components);return r.createElement(p.Provider,{value:e},t.children)},d={inlineCode:"code",wrapper:function(t){var e=t.children;return r.createElement(r.Fragment,{},e)}},k=r.forwardRef((function(t,e){var a=t.components,n=t.mdxType,l=t.originalType,p=t.parentName,m=i(t,["components","mdxType","originalType","parentName"]),k=o(a),g=n,N=k["".concat(p,".").concat(g)]||k[g]||d[g]||l;return a?r.createElement(N,u(u({ref:e},m),{},{components:a})):r.createElement(N,u({ref:e},m))}));function g(t,e){var a=arguments,n=e&&e.mdxType;if("string"==typeof t||n){var l=a.length,u=new Array(l);u[0]=k;var i={};for(var p in e)hasOwnProperty.call(e,p)&&(i[p]=e[p]);i.originalType=t,i.mdxType="string"==typeof t?t:n,u[1]=i;for(var o=2;o<l;o++)u[o]=a[o];return r.createElement.apply(null,u)}return r.createElement.apply(null,a)}k.displayName="MDXCreateElement"},9999:function(t,e,a){a.r(e),a.d(e,{assets:function(){return p},contentTitle:function(){return u},default:function(){return d},frontMatter:function(){return l},metadata:function(){return i},toc:function(){return o}});var r=a(3117),n=(a(7294),a(3905));const l={sidebar_position:4},u="Supported metrics",i={unversionedId:"supported-metrics",id:"supported-metrics",title:"Supported metrics",description:"In Mesmer we support 3 types of metrics:",source:"@site/../mesmer-docs/target/mdoc/supported-metrics.md",sourceDirName:".",slug:"/supported-metrics",permalink:"/docs/supported-metrics",draft:!1,tags:[],version:"current",sidebarPosition:4,frontMatter:{sidebar_position:4},sidebar:"tutorialSidebar",previous:{title:"Configuration",permalink:"/docs/configuration"},next:{title:"ZIO Example",permalink:"/docs/zio-example"}},p={},o=[{value:"Akka core",id:"akka-core",level:2},{value:"Akka Cluster",id:"akka-cluster",level:2},{value:"Akka Persistence",id:"akka-persistence",level:2},{value:"Akka Streams (experimental)",id:"akka-streams-experimental",level:2},{value:"ZIO Executor Metrics",id:"zio-executor-metrics",level:2},{value:"ZIO JVM Metrics",id:"zio-jvm-metrics",level:2},{value:"ZIO Runtime Metrics",id:"zio-runtime-metrics",level:2}],m={toc:o};function d(t){let{components:e,...a}=t;return(0,n.kt)("wrapper",(0,r.Z)({},m,a,{components:e,mdxType:"MDXLayout"}),(0,n.kt)("h1",{id:"supported-metrics"},"Supported metrics"),(0,n.kt)("p",null,"In Mesmer we support 3 types of metrics:"),(0,n.kt)("ul",null,(0,n.kt)("li",{parentName:"ul"},"gauge - for sampled values"),(0,n.kt)("li",{parentName:"ul"},"counter - monotonic counter"),(0,n.kt)("li",{parentName:"ul"},"histograms - for recording value distributions")),(0,n.kt)("h2",{id:"akka-core"},"Akka core"),(0,n.kt)("table",null,(0,n.kt)("thead",{parentName:"table"},(0,n.kt)("tr",{parentName:"thead"},(0,n.kt)("th",{parentName:"tr",align:null},"Metric"),(0,n.kt)("th",{parentName:"tr",align:null},"Type"))),(0,n.kt)("tbody",{parentName:"table"},(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Running actors"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Mailbox size"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Stashed messaged"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Mailbox time"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Processed messages"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Processing time"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Sent messages"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")))),(0,n.kt)("h2",{id:"akka-cluster"},"Akka Cluster"),(0,n.kt)("table",null,(0,n.kt)("thead",{parentName:"table"},(0,n.kt)("tr",{parentName:"thead"},(0,n.kt)("th",{parentName:"tr",align:null},"Metric"),(0,n.kt)("th",{parentName:"tr",align:null},"Type"))),(0,n.kt)("tbody",{parentName:"table"},(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Shards per region"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Reachable nodes"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Unreachable nodes"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Entities per region"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Shard regions on node"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Entities on node"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Nodes down"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")))),(0,n.kt)("h2",{id:"akka-persistence"},"Akka Persistence"),(0,n.kt)("table",null,(0,n.kt)("thead",{parentName:"table"},(0,n.kt)("tr",{parentName:"thead"},(0,n.kt)("th",{parentName:"tr",align:null},"Metric"),(0,n.kt)("th",{parentName:"tr",align:null},"Type"))),(0,n.kt)("tbody",{parentName:"table"},(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Persisted events"),(0,n.kt)("td",{parentName:"tr",align:null},"histogram")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Event persistence time"),(0,n.kt)("td",{parentName:"tr",align:null},"histogram")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Recovery total"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Recovery time"),(0,n.kt)("td",{parentName:"tr",align:null},"histogram")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Snapshots"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")))),(0,n.kt)("h2",{id:"akka-streams-experimental"},"Akka Streams (experimental)"),(0,n.kt)("table",null,(0,n.kt)("thead",{parentName:"table"},(0,n.kt)("tr",{parentName:"thead"},(0,n.kt)("th",{parentName:"tr",align:null},"Metric"),(0,n.kt)("th",{parentName:"tr",align:null},"Type"))),(0,n.kt)("tbody",{parentName:"table"},(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Running streams"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Running operators per stream"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Running operators"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Stream throughput"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Operator throughput"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Operator processing time"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")))),(0,n.kt)("h2",{id:"zio-executor-metrics"},"ZIO Executor Metrics"),(0,n.kt)("table",null,(0,n.kt)("thead",{parentName:"table"},(0,n.kt)("tr",{parentName:"thead"},(0,n.kt)("th",{parentName:"tr",align:null},"Metric"),(0,n.kt)("th",{parentName:"tr",align:null},"Type"))),(0,n.kt)("tbody",{parentName:"table"},(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Executor Capacity"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Executor Concurrency"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Executor Dequeued Count"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Executor Enqueued Count"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Executor Size"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Executor Worker Count"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")))),(0,n.kt)("h2",{id:"zio-jvm-metrics"},"ZIO JVM Metrics"),(0,n.kt)("table",null,(0,n.kt)("thead",{parentName:"table"},(0,n.kt)("tr",{parentName:"thead"},(0,n.kt)("th",{parentName:"tr",align:null},"Metric"),(0,n.kt)("th",{parentName:"tr",align:null},"Type"))),(0,n.kt)("tbody",{parentName:"table"},(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Buffer Pool Capacity Bytes"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Buffer Pool Used Buffers"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Buffer Pool Used Bytes"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Classes Loaded"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Classes Loaded Total"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Classes Unloaded Total"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM GC Collection Seconds Count"),(0,n.kt)("td",{parentName:"tr",align:null},"histogram")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM GC Collection Seconds Sum"),(0,n.kt)("td",{parentName:"tr",align:null},"histogram")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Info"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Bytes Committed"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Bytes Init"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Bytes Max"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Bytes Used"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Pool Allocated Bytes Total"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Pool Bytes Committed"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Pool Bytes Init"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Pool Bytes Max"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Memory Pool Bytes Used"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Threads Current"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Threads Daemon"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Threads Deadlocked"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Threads Deadlocked Monitor"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Threads Peak"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"JVM Threads Started Total"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")))),(0,n.kt)("h2",{id:"zio-runtime-metrics"},"ZIO Runtime Metrics"),(0,n.kt)("table",null,(0,n.kt)("thead",{parentName:"table"},(0,n.kt)("tr",{parentName:"thead"},(0,n.kt)("th",{parentName:"tr",align:null},"Metric"),(0,n.kt)("th",{parentName:"tr",align:null},"Type"))),(0,n.kt)("tbody",{parentName:"table"},(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Forwarded Null"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Process CPU Seconds Total"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Process Max FDS"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Process Open FDS"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Process Resident Memory Bytes"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Process Start Time Seconds"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"Process Virtual Memory Bytes"),(0,n.kt)("td",{parentName:"tr",align:null},"gauge")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"ZIO Fiber Failures"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"ZIO Fiber Started"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")),(0,n.kt)("tr",{parentName:"tbody"},(0,n.kt)("td",{parentName:"tr",align:null},"ZIO Fiber Successes"),(0,n.kt)("td",{parentName:"tr",align:null},"counter")))))}d.isMDXComponent=!0}}]);