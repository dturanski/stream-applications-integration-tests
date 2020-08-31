CREATE DATABASE IF NOT EXISTS test;
USE test;
CREATE TABLE IF NOT EXISTS People (
	 id INT NOT NULL AUTO_INCREMENT,
	 name VARCHAR(255) NOT NULL,
	 street VARCHAR(255) NOT NULL,
	 city VARCHAR(255) NOT NULL,
	 deleted CHAR(1) NOT NULL,
	 PRIMARY KEY (id));
INSERT INTO People (name, street, city, deleted) VALUES ("Bart Simpson", "Main Street", "Springfield","N");