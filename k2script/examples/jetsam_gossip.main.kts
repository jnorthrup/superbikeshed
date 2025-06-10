#!/usr/bin/env k2script

@file:DependsOn("k2script:engine")
@file:DependsOn("k2script:trikeshed")

import k2script.engine.JetsamGossip
import k2script.engine.gatherJetsam
import k2script.engine.gossipToCouch
import k2script.engine.gossipToIPFS

fun main() {
    println("Gathering Jetsam...")
    val jetsam = gatherJetsam()
    
    println("Gossiping to CouchDB...")
    gossipToCouch(jetsam)
    
    println("Gossiping to IPFS...")
    gossipToIPFS(jetsam)
    
    println("Jetsam gossip complete!")
} 