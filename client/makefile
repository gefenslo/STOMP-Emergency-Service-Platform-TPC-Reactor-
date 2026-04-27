CFLAGS:=-c -Wall -Weffc++ -g -std=c++11 -Iinclude
LDFLAGS:=-lboost_system -lpthread
# TODO change the makefile for StompEMIlient
all: StompEMIClient


StompEMIClient: bin/StompClient.o bin/event.o 
	g++ -o bin/StompEMIClient bin/StompClient.o bin/event.o  $(LDFLAGS)


bin/event.o: src/event.cpp
	g++ $(CFLAGS) -o bin/event.o src/event.cpp

bin/StompClient.o: src/StompClient.cpp
	g++ $(CFLAGS) -o bin/StompClient.o src/StompClient.cpp



.PHONY: clean
clean:
	rm -f bin/*
	