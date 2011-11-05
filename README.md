
* originalのもの https://github.com/harrah/browse を微妙に改造してます
* 基本的なことはもとの README を読んでください https://github.com/xuwei-k/browse/blob/master/README_original.md

### 改造した点

* すべての directory に index.html を吐くようにしました
* ファイルの行数を index.html に表示するようにしました
* sub directory 一覧をindex.htmlに表示するようにしました
* (gh-pagesに載せるとき微妙に不便だったので) headerにちょっと付け足して、htmlのキャッシュを無効にしました
* 基本的にindex.htmlのaタグに、 ` target="_brank" ` を加えて、新しいタブで開くようにしました。

### 注意点など

* Pathの区切り文字の処理の関係で、windowsでちゃんと動くかどうかあやしい
* 2.9.1でしか使ってないからほかのversionで動くかどうかしらない。そもそもjarは2.9.1のものしかmaven repositoryにあげていない


### 使い方

github page に maven リポジトリがあってそこに置いてあります。
なので、例えばsbtならば以下を`build.sbt`に書けば使えるはずです

```scala
resolvers += "xuwei-k repo" at "http://xuwei-k.github.com/mvn"
```

それ以外は全くオリジナルと同じですが、例えば以下のように設定します

```scala
addCompilerPlugin("org.scala-tools.sxr" % "sxr_2.9.1" % "0.2.8-SNAPSHOT"

scalacOptions <+= scalaSource in Compile map { "-P:sxr:base-directory:" + _.getAbsolutePath }
```


### Sample

自分が改造したversionのsxrで作成した scala 自体のlibrary や compiler の sxr を勝手にgh-pagesに上げちゃってるので参考までに(´・ω・)っ

http://xuwei-k.github.com/scala-library-sxr/scala-library-2.9.1/

http://xuwei-k.github.com/scala-compiler-sxr/scala-compiler-2.9.1/

### TODO
* ファイルの行番号表示とかやりたい