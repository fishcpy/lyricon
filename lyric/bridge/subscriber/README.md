# Lyricon 歌词订阅开发指南

## 一、添加依赖

![version](https://img.shields.io/maven-central/v/io.github.proify.lyricon/subscriber)

在`build.gradle.kts` 中添加依赖：

```kotlin
implementation("io.github.proify.lyricon:subscriber:0.1.70")
```

## 二、创建 `LyriconSubscriber`

```kotlin
//import io.github.proify.lyricon.subscriber.LyriconFactory

val subscriber = LyriconFactory.createSubscriber(context)

//订阅当前焦点播放器
subscriber.subscribeActivePlayer(/*...*/)

//添加连接监听
subscriber.addConnectionListener(/*...*/)

//注册
subscriber.register()
```