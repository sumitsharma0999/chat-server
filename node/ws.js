const kafka = require('kafka-node');
const config = require('./config');
var KeyedMessage = kafka.KeyedMessage;

// Init after some time to let the redis/kafka instance to be up
setTimeout(init, 30000);

function init() {
  console.log('Initialization starting .....');
  var producer;
  try {
    const Producer = kafka.Producer;
    const client = new kafka.KafkaClient({kafkaHost: config.kafka_server});
    producer = new Producer(client);

    producer.on('ready', function() {
      console.log("Producer is ready to send");

      producer.createTopics(["special_waitingToBeMatched"], true, function(err, result) {
        if (err) {
          console.log("failed to create sepcial topic: " + err);
        }
      });
    });

    producer.on('error', function(err) {
      console.trace('Producer can not send any data ' + err.stack);
      //throw err;
    });
  }
  catch(e) {
    console.log(e);
  }

  var WebSocketServer = require('ws').Server,
    wss = new WebSocketServer({port: 40510}),
    os = require('os'),
    hostname = os.hostname(),
    redis = require("redis"),
    redisClient = redis.createClient(config.redis_url);

  redisClient.on("error", function (err) {
      console.log("Error " + err);
  });  

  var wsConnections = {};
  var kakfaConsumers = {};

  wss.on('connection', function (ws) {
    ws.on('message', function (dataStr) {
      var data = JSON.parse(dataStr);
      console.log('received: %s', dataStr);
      if (data.type === "username") {
        var username = data.value;
        console.log("User '" + username + "' connected to " + hostname);
        ws.username = username;

        handleUserConnected(ws);

        ws.send(JSON.stringify({
          type: 'connection',
          msg: "Waiting for another player to join. You are connected to " + hostname
        }));

      } else if (data.type === 'msg') {
        var msgText = data.msgText;
        
        // Find game id for this user (from redis)
        redisClient.get("game:"+ws.username, function(error, result){
          if (error) {
            console.log(error);
          }
          else {
            var gameId = result;

            // Find the other user in the game
            redisClient.lrange(gameId, 0, 1, function(error, result){
              var otherUser = (result[0] === ws.username) ? result[1] : result[0];
              // TODO: Add msg to target's topic
              sendToOtherUser(otherUser, msgText);
            });
          }
        });
      }
    });

    ws.on('close', function() {
      delete wsConnections[ws.username];
    });
  })

  function handleUserConnected(ws) {
    // Add the connection to map
    wsConnections[ws.username] = ws;

    // Create a listerner for the user
    try {
      const Consumer = kafka.Consumer;
      const client = new kafka.KafkaClient({kafkaHost: config.kafka_server});
      client.on('ready', function(){
        console.log("Consumer's client is ready");

        // create a topic for this user
        producer.createTopics([ws.username], true, function(err, result) {
          if (err) {
            console.log("failed to create topic: " + err);
          }
          else {
            let consumer = new Consumer(
              client,
              [{
                topic: ws.username,
                offset:0
              }],
              {
                groupId: 'node-group',
                autoCommit: false,
                fetchMaxWaitMs: 1000,
                fetchMaxBytes: 1024 * 1024,
                encoding: 'utf8',
                fromOffset: true
              }
            );

            consumer.on('message', function(message) {
              console.log('received message for consumer of ' + ws.username);
              console.log(
                'kafka-> ',
                message.value
              );
        
              var msgStr = message.value;

              if (msgStr.startsWith("connection_")) {
                // Connection message, ready to communicate
                var opponent = msgStr.substring(11);
                ws.send(JSON.stringify({
                  type: 'connection',
                  msg: 'You are now connected with ' + opponent
                }))
              }
              else {
                ws.send(JSON.stringify({
                  type: 'msg',
                  msgText: message.value
                }));
              }
            })
            consumer.on('error', function(err) {
              console.log('error in kafka consumer', err);
            });

            // Add it to list of players waiting
            // Do it after consumer has been created so that consumer can recive messages
            let payloads = [
              {
                topic: "special_waitingToBeMatched",
                messages: ws.username
              }
            ];

            producer.send(payloads, (err, data) => {
              if (err) {
                console.log('Failed to add ' + ws.username + ' to waiting queue');
              } else {
                console.log('Added '+ws.username+' to waiting queue');
              }
            });
          }
        })
      })
    }
    catch(e) {
      console.log("Error occured in handleUserConnected" + e);
    }
  }

  function sendToOtherUser(otherUser, msgText) {
    let payloads = [
      {
        topic: otherUser,
        messages: msgText
      }
    ];
    producer.send(payloads, (err, data) => {
      if (err) {
        console.log('[kafka-producer -> '+otherUser+']: broker update failed');
      } else {
        console.log('[kafka-producer -> '+otherUser+']: broker update success');
      }
    });
  }
}
