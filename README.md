BIOSocks
===

## Background/About this project

BIOSocks  is an socks5 tool to surf the Internet

## Requirements

Java 
Chrome & SwitchyOmega

## Usage
├── target  
├── biosocks-0.0.1.jar                            
├── client.json    
├── server.json                  
├── client.sh  
└── server.sh                           

### Client

config.json
```json
{
  "server": "server ip", 
  "serverPort": 9501,
  "client": "0.0.0.0",
  "clientPort": 9101
}
```
start client(a local SOCKS5 proxy)
```$xslt
java -jar ./target/biosocks-0.0.1.jar client
```
### Server

server.json
```json
{
  "server": "0.0.0.0",
  "serverPort": 9501
}

```
start server
```
java -jar ./target/biosocks-0.0.1.jar server
```
### connect proxy
client是一个socks5代理
server部署在外网服务器上
使用Chrome & SwitchyOmega连接本地的socks5代理实现网上冲浪





