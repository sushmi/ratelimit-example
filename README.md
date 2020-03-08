# Rate Limiter

Rate Limiter implements Token Bucket Strategy to limit requests.

Token Bucket: It defines some predefined allowed tokens and serves the request until tokens are available.
Once token is consumed completely, it starts to throw `RateLimitExceededException`. 
The allowed tokens/ permits are stored using `ConcurrentHashMap`. 

The user quota that defines the number of permits is stored in Local cache. 
The cache should be filled when app starts.

Usage: 
1. `sbt run`
2. Test app is running
   `curl localhost:8080/ping`
2. You can test ratelimiting endpoint via - the user has QRS limit as 2 per min
   `curl localhost:8080/anything -H "Content-Type: application/json" -X POST -d '{"userId":"beaaed71-f099-4bcd-88d3-62ee36576c04"}'`


