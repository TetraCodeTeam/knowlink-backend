-- MySQL dump 10.13  Distrib 9.0.1, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: knowlink_db
-- ------------------------------------------------------
-- Server version	9.0.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `academic_material`
--

DROP TABLE IF EXISTS `academic_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `academic_material` (
  `academic_material_id` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `available` bit(1) NOT NULL,
  `file_url` varchar(255) DEFAULT NULL,
  `material_type` enum('PDF','PNG','XLSX') NOT NULL,
  `name` varchar(255) NOT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `reports_count` int NOT NULL,
  `size_in_bytes` bigint DEFAULT NULL,
  `storage_path` varchar(255) NOT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `tutor_subject_id` binary(16) NOT NULL,
  PRIMARY KEY (`academic_material_id`),
  KEY `FKmaaligt02sqqu1ga9llkek2a0` (`tutor_subject_id`),
  CONSTRAINT `FKmaaligt02sqqu1ga9llkek2a0` FOREIGN KEY (`tutor_subject_id`) REFERENCES `tutor_subject` (`tutor_subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `academic_material`
--

LOCK TABLES `academic_material` WRITE;
/*!40000 ALTER TABLE `academic_material` DISABLE KEYS */;
INSERT INTO `academic_material` VALUES (_binary '©JKô└Eû Zq¨\█\ß',_binary '',_binary '',NULL,'PDF','material qu├¡mica','Balance-de-masa-y-energia.pdf',0,924698,'9faade7c-8964-4d6e-b83f-9f22172f57fc/b6072f9b-61da-4985-8671-7ffe6e991059-Balance-de-masa-y-energia.pdf','2026-09-30 19:53:48.864529',_binary '▒æH,└\╬@┤ñ0\Ôc×ö*g');
/*!40000 ALTER TABLE `academic_material` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `availability_block`
--

DROP TABLE IF EXISTS `availability_block`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `availability_block` (
  `availability_block_id` binary(16) NOT NULL,
  `auto_generated` bit(1) NOT NULL,
  `available` bit(1) NOT NULL,
  `date` date NOT NULL,
  `end_time` time(6) NOT NULL,
  `repeat_weekly` bit(1) NOT NULL,
  `start_time` time(6) NOT NULL,
  `tutor_profile_id` binary(16) NOT NULL,
  PRIMARY KEY (`availability_block_id`),
  KEY `FKkl0e5sbabo0msm7t05335atty` (`tutor_profile_id`),
  CONSTRAINT `FKkl0e5sbabo0msm7t05335atty` FOREIGN KEY (`tutor_profile_id`) REFERENCES `tutor_profile` (`tutor_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `availability_block`
--

LOCK TABLES `availability_block` WRITE;
/*!40000 ALTER TABLE `availability_block` DISABLE KEYS */;
INSERT INTO `availability_block` VALUES (_binary '½wixLW£WkT[╗°Ü',_binary '',_binary '','2026-10-17','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'ú8Ä!\"Nb¥gy««_\µ',_binary '',_binary '','2026-12-26','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '\r2¼?DC©ïkê\÷\¶▄Ü\µ',_binary '',_binary '','2026-11-07','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '\¯\═\┬\ýtL╗Æ\╩\Z7ð©g',_binary '',_binary '','2026-11-21','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'k»CC@╚Éz:6\╔~-N',_binary '',_binary '','2026-10-24','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'SáQoK4¡\┌0î»ô\Î\¤',_binary '\0',_binary '','2026-09-20','23:00:00.000000',_binary '\0','20:00:00.000000',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '!╗\ßz\÷!Lñ│░\¦*(j',_binary '\0',_binary '','2026-10-03','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '&<8ƒSHÕïí\Z1»\═\¸C',_binary '',_binary '','2026-12-12','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '9üïI╣F┬çl·N╠¢\▄8',_binary '',_binary '','2026-10-24','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary ';bW×¹qCë╗\Ã\¤#À3,«',_binary '',_binary '','2026-11-28','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'D\ÞUö\Ò{DdÆónä\Þ\'\ý',_binary '',_binary '','2026-11-14','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'Eâ5LJ░\Ì\´\¯ù 0',_binary '',_binary '','2026-10-31','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'J\Ù┘Éæ¢BÃ×┴■\╩lIm',_binary '',_binary '','2026-10-31','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'fÂèg·IU¥Ü¹:4x\Ïm',_binary '',_binary '','2026-12-05','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'i\▄M\╩r\\@¦ØP?4ïu\‗',_binary '',_binary '','2026-11-07','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'j¿ƒ\´╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-03','19:00:00.000000',_binary '\0','18:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j¬\ã═╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-05','11:00:00.000000',_binary '\0','10:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j¼)^╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-08','20:00:00.000000',_binary '\0','19:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j¡¥╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-10','19:00:00.000000',_binary '\0','18:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j«╩╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-12','11:00:00.000000',_binary '\0','10:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j»H╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-15','20:00:00.000000',_binary '\0','19:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j░*b╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-17','19:00:00.000000',_binary '\0','18:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j▓\Í|╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-19','11:00:00.000000',_binary '\0','10:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j│\ı:╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-22','20:00:00.000000',_binary '\0','19:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j┤┴\§╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-24','19:00:00.000000',_binary '\0','18:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'jÁ\▄╔╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-26','11:00:00.000000',_binary '\0','10:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'jÂ\¦.╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-29','20:00:00.000000',_binary '\0','19:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'jÀ\╬╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-09','19:00:00.000000',_binary '\0','18:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j║ÿC╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-16','19:00:00.000000',_binary '\0','18:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j╗\Û\\╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-09-23','19:00:00.000000',_binary '\0','18:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j¢X╔╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-10-01','20:00:00.000000',_binary '\0','19:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'j┐O¦╝\Ú\±ïH×¢\Õë',_binary '\0',_binary '','2026-10-08','20:00:00.000000',_binary '\0','19:00:00.000000',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 's#w▓N\ÓGÌïr|Àwmº',_binary '',_binary '','2026-10-10','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'éaT&\0DÉú╣}Â┴\Ðz',_binary '\0',_binary '','2026-09-20','23:00:00.000000',_binary '\0','19:00:00.000000',_binary '	\0î┤r\±▓\ËH×¢\Õë'),(_binary 'é▒9ó YIZøh/Ä+¼\─\Ó',_binary '\0',_binary '','2026-09-24','17:00:00.000000',_binary '\0','08:00:00.000000',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary 'â\┘\ÊD\ÚB)à5&ÖuÇÀN',_binary '\0',_binary '','2026-09-23','17:00:00.000000',_binary '\0','08:00:00.000000',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary 'ìG\Ë\0└║Gô║\ýåE\rgwd',_binary '\0',_binary '','2026-10-03','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'ÉóíühqMÑÙ│ÜCT|',_binary '\0',_binary '','2026-09-21','17:00:00.000000',_binary '\0','08:00:00.000000',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary 'Ö[\þ_äI/ªr| »$\n',_binary '',_binary '','2026-12-12','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'á\Ðl²Ø\ÐF_ùI¡R\¸\'',_binary '',_binary '','2026-11-28','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary 'ó║s\¸î\ÈFn»╝\'ðÆnz\´',_binary '',_binary '','2026-11-21','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '¬\╠\'?\µBÊ║q|6>▒â',_binary '',_binary '','2026-12-19','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '½\'@e\┬@\¸£\┬/\‗?îF ',_binary '',_binary '','2026-10-10','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '\ãld\"■\├LßÂêüè&\Óƒ',_binary '',_binary '','2026-11-14','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '\┌nQó\╬A\Ò¼\╚\═V³│Ø',_binary '',_binary '','2026-12-05','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '\Ó2)║~rOÇÀ*\"óÑ\Ý\Z3',_binary '',_binary '','2026-10-17','12:00:00.000000',_binary '','09:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '\ý}\▄r\÷1I─┐v▄╝ \█b',_binary '\0',_binary '','2026-09-20','14:30:00.000000',_binary '\0','08:00:00.000000',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '\¸\Ô%RÑM\\¥ôRÆ\§\¤\Ã',_binary '\0',_binary '','2026-09-22','17:00:00.000000',_binary '\0','08:00:00.000000',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '³1ô\┼■×F░╣¹g=Bêº',_binary '',_binary '','2026-12-26','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '³\ÊÃÀj▓LÃú:)[1©',_binary '',_binary '','2026-12-19','17:30:00.000000',_binary '','14:00:00.000000',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^');
/*!40000 ALTER TABLE `availability_block` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `availability_week_customization`
--

DROP TABLE IF EXISTS `availability_week_customization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `availability_week_customization` (
  `availability_week_customization_id` binary(16) NOT NULL,
  `week_start` date NOT NULL,
  `tutor_profile_id` binary(16) NOT NULL,
  PRIMARY KEY (`availability_week_customization_id`),
  UNIQUE KEY `UKoy79e1wksl4sgj7mvkrslpj0y` (`tutor_profile_id`,`week_start`),
  CONSTRAINT `FKk2oqol381clyjgxte3d2u9wam` FOREIGN KEY (`tutor_profile_id`) REFERENCES `tutor_profile` (`tutor_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `availability_week_customization`
--

LOCK TABLES `availability_week_customization` WRITE;
/*!40000 ALTER TABLE `availability_week_customization` DISABLE KEYS */;
INSERT INTO `availability_week_customization` VALUES (_binary 'LZY\Ï¹\þH\ÝÇmúºêu\Õ','2026-09-28',_binary '╩┤r\±▓\ËH×¢\Õë'),(_binary '\┬╗\╦cC¥\ß▀®\µ╔ä','2026-09-14',_binary '	\0î┤r\±▓\ËH×¢\Õë'),(_binary '~0%ùÀCÈò╚ö\´\ãN<o','2026-09-14',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '×xJ!\Ú\‗FÏÉ®\¯n\´-\rå','2026-09-21',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '\═-ò°\Ò\Ý@i▓7óK','2026-09-28',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^');
/*!40000 ALTER TABLE `availability_week_customization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `booking`
--

DROP TABLE IF EXISTS `booking`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking` (
  `booking_id` binary(16) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `booking_status` enum('BOOKED','CANCELLED','COMPLETED','EXPIRED','IN_PROGRESS','NOT_CONFIRMED','NOT_FULFILLED_BY_TUTOR','PENDING','SESSION_NOT_HELD') NOT NULL,
  `completion_notification_sent` bit(1) NOT NULL,
  `confirmation_token` varchar(255) DEFAULT NULL,
  `confirmation_token_attempts` int NOT NULL,
  `confirmation_token_expiration` datetime(6) DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_time` time(6) NOT NULL,
  `last_confirmation_reminder_at` datetime(6) DEFAULT NULL,
  `modality` enum('BOTH','IN_PERSON','VIRTUAL') NOT NULL,
  `session_date` date NOT NULL,
  `start_time` time(6) NOT NULL,
  `topic` text,
  `virtual_session_link` varchar(255) DEFAULT NULL,
  `student_id` binary(16) NOT NULL,
  `time_slot_id` binary(16) NOT NULL,
  `tutor_id` binary(16) NOT NULL,
  `tutor_subject_id` binary(16) NOT NULL,
  PRIMARY KEY (`booking_id`),
  UNIQUE KEY `uk_active_booking_slot_start` (`time_slot_id`,`start_time`),
  KEY `FK7ynttc3ox5iseun7krrf80r1v` (`student_id`),
  KEY `FKl4fgmxu0wgy63auhms20ooa50` (`tutor_id`),
  KEY `FKbsx6i1vtge9e0uy0eygdhvgip` (`tutor_subject_id`),
  CONSTRAINT `FK7ynttc3ox5iseun7krrf80r1v` FOREIGN KEY (`student_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKbsx6i1vtge9e0uy0eygdhvgip` FOREIGN KEY (`tutor_subject_id`) REFERENCES `tutor_subject` (`tutor_subject_id`),
  CONSTRAINT `FKkbrui904858xdf0e5xqnd4hcf` FOREIGN KEY (`time_slot_id`) REFERENCES `time_slot` (`time_slot_id`),
  CONSTRAINT `FKl4fgmxu0wgy63auhms20ooa50` FOREIGN KEY (`tutor_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `booking`
--

LOCK TABLES `booking` WRITE;
/*!40000 ALTER TABLE `booking` DISABLE KEYS */;
INSERT INTO `booking` VALUES (_binary 'WjLN6║\▀R\ý╗*',3090.00,'BOOKED',_binary '',NULL,0,NULL,NULL,'2026-09-19 21:45:19.778417','20:00:00.000000',NULL,'VIRTUAL','2026-09-20','19:00:00.000000','mapas de karnaug',NULL,_binary '63fce54a-b46f-11',_binary '\¶\╦\█2╝gE\Zæ\÷~\¦Ç',_binary '63fdb05b-b46f-11',_binary '\nÑ┤r\±▓\ËH×¢\Õë'),(_binary '\'ü╗ÿì½K1ª½\¦\nøÈÜ\Ã',3605.00,'BOOKED',_binary '',NULL,0,NULL,NULL,'2026-09-20 00:24:40.449411','09:00:00.000000',NULL,'VIRTUAL','2026-09-20','08:00:00.000000','test',NULL,_binary '63fce54a-b46f-11',_binary 'Âêè\ı\ðH7│áÆ \Ì\µj',_binary '63fdb17c-b46f-11',_binary 'G┤r\±▓\ËH×¢\Õë'),(_binary 'j¬\Z╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-03 19:00:00.000000','2026-09-01 11:00:00.000000','19:00:00.000000',NULL,'VIRTUAL','2026-09-03','18:00:00.000000','Balance general de materia y energ├¡a','https://meet.jit.si/knowlink-clase-01',_binary '63fce54a-b46f-11',_binary 'j¿® ╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '\±D$\Ù{oB¥Är╝1┐\Ò:\▀'),(_binary 'j½\Î	╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,1,NULL,'2026-09-05 11:00:00.000000','2026-09-02 09:30:00.000000','11:00:00.000000',NULL,'VIRTUAL','2026-09-05','10:00:00.000000','Primeros principios y estados de agregaci├│n','https://meet.jit.si/knowlink-clase-02',_binary '63fd0f9b-b46f-11',_binary 'j¬╩ò╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'X\r©9?~Gô¿9\═Qu\Ï\Þ'),(_binary 'j¼└\Z╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-08 20:00:00.000000','2026-09-05 16:20:00.000000','20:00:00.000000',NULL,'VIRTUAL','2026-09-08','19:00:00.000000','Estructura at├│mica y enlace qu├¡mico','https://meet.jit.si/knowlink-clase-03',_binary ' ╩å\┬\ãM2╝║\ı<	\Ê~',_binary 'j¼-4╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '1▓\Û2êäFlæ\'\Ì\┘\\'),(_binary 'j¡╝]╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-10 19:00:00.000000','2026-09-07 10:05:00.000000','19:00:00.000000',NULL,'VIRTUAL','2026-09-10','18:00:00.000000','Operaciones b├ísicas en biorreactores','https://meet.jit.si/knowlink-clase-04',_binary 'äZ>░Gá\¦OP╠»[c',_binary 'j¡U╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'h\ÚHùØ_Eoü\═\Ù\ð}qø'),(_binary 'j«Ãë╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,2,NULL,'2026-09-12 11:00:00.000000','2026-09-09 14:40:00.000000','11:00:00.000000',NULL,'VIRTUAL','2026-09-12','10:00:00.000000','Balance por unidad de proceso','https://meet.jit.si/knowlink-clase-05',_binary ' ╩å\┬\ãM2╝║\ı<	\Ê~',_binary 'j«	Z╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '\±D$\Ù{oB¥Är╝1┐\Ò:\▀'),(_binary 'j»Ðí╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-15 20:00:00.000000','2026-09-12 12:10:00.000000','20:00:00.000000',NULL,'VIRTUAL','2026-09-15','19:00:00.000000','Sistemas y fases: regla de las fases','https://meet.jit.si/knowlink-clase-06',_binary '63fce54a-b46f-11',_binary 'j» >╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'X\r©9?~Gô¿9\═Qu\Ï\Þ'),(_binary 'j▓xÂ╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-17 19:00:00.000000','2026-09-14 17:00:00.000000','19:00:00.000000',NULL,'VIRTUAL','2026-09-17','18:00:00.000000','Cristalograf├¡a y defectos en redes','https://meet.jit.si/knowlink-clase-07',_binary 'äZ>░Gá\¦OP╠»[c',_binary 'j░-²╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '1▓\Û2êäFlæ\'\Ì\┘\\'),(_binary 'j│èƒ╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,1,NULL,'2026-09-19 11:00:00.000000','2026-09-16 09:15:00.000000','11:00:00.000000',NULL,'VIRTUAL','2026-09-19','10:00:00.000000','Cin├®tica enzim├ítica: Michaelis-Menten','https://meet.jit.si/knowlink-clase-08',_binary '63fd0f9b-b46f-11',_binary 'j▓\█\Ò╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'h\ÚHùØ_Eoü\═\Ù\ð}qø'),(_binary 'j┤uÉ╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-22 20:00:00.000000','2026-09-19 13:00:00.000000','20:00:00.000000',NULL,'VIRTUAL','2026-09-22','19:00:00.000000','Balances acumulados con recirculaci├│n','https://meet.jit.si/knowlink-clase-09',_binary 'äZ>░Gá\¦OP╠»[c',_binary 'j│\Ï█╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '\±D$\Ù{oB¥Är╝1┐\Ò:\▀'),(_binary 'jÁc┐╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-24 19:00:00.000000','2026-09-21 10:45:00.000000','19:00:00.000000',NULL,'VIRTUAL','2026-09-24','18:00:00.000000','Cin├®tica qu├¡mica: ├│rdenes de reacci├│n','https://meet.jit.si/knowlink-clase-10',_binary ' ╩å\┬\ãM2╝║\ı<	\Ê~',_binary 'j┤┼¡╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'X\r©9?~Gô¿9\═Qu\Ï\Þ'),(_binary 'jÂì¿╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,0,NULL,'2026-09-26 11:00:00.000000','2026-09-23 15:30:00.000000','11:00:00.000000',NULL,'VIRTUAL','2026-09-26','10:00:00.000000','Pol├¡meros y materiales compuestos','https://meet.jit.si/knowlink-clase-11',_binary '63fce54a-b46f-11',_binary 'jÁ\ß\'╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '1▓\Û2êäFlæ\'\Ì\┘\\'),(_binary 'jÀüA╝\Ú\±ïH×¢\Õë',15450.00,'COMPLETED',_binary '',NULL,3,NULL,'2026-09-29 20:00:00.000000','2026-09-26 11:50:00.000000','20:00:00.000000',NULL,'VIRTUAL','2026-09-29','19:00:00.000000','Purificaci├│n de prote├¡nas','https://meet.jit.si/knowlink-clase-12',_binary ' ╩å\┬\ãM2╝║\ı<	\Ê~',_binary 'jÂ\Ó└╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'h\ÚHùØ_Eoü\═\Ù\ð}qø'),(_binary 'jÀ\ÍK╝\Ú\±ïH×¢\Õë',15450.00,'CANCELLED',_binary '',NULL,0,NULL,NULL,'2026-09-05 10:00:00.000000','19:00:00.000000',NULL,'VIRTUAL','2026-09-09','18:00:00.000000','Balance de materia con especies inerts','https://meet.jit.si/knowlink-clase-13',_binary '63fd0f9b-b46f-11',_binary 'jÀ\Ð■╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '\±D$\Ù{oB¥Är╝1┐\Ò:\▀'),(_binary 'j║ƒä╝\Ú\±ïH×¢\Õë',15450.00,'CANCELLED',_binary '',NULL,0,NULL,NULL,'2026-09-12 09:00:00.000000','19:00:00.000000',NULL,'VIRTUAL','2026-09-16','18:00:00.000000','Potencial qu├¡mico y equilibrio','https://meet.jit.si/knowlink-clase-14',_binary '63fce54a-b46f-11',_binary 'j║£╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'X\r©9?~Gô¿9\═Qu\Ï\Þ'),(_binary 'j╝áw╝\Ú\±ïH×¢\Õë',15450.00,'NOT_CONFIRMED',_binary '',NULL,5,NULL,NULL,'2026-09-18 16:00:00.000000','19:00:00.000000',NULL,'VIRTUAL','2026-09-23','18:00:00.000000','Materiales cer├ímicos y vidrios','https://meet.jit.si/knowlink-clase-15',_binary 'äZ>░Gá\¦OP╠»[c',_binary 'j╗\Ý▀╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '1▓\Û2êäFlæ\'\Ì\┘\\'),(_binary 'j¥nå╝\Ú\±ïH×¢\Õë',15450.00,'BOOKED',_binary '',NULL,0,NULL,NULL,'2026-09-28 12:00:00.000000','20:00:00.000000',NULL,'VIRTUAL','2026-10-01','19:00:00.000000','Resoluci├│n de ejercicios de balances','https://meet.jit.si/knowlink-clase-16',_binary '63fce54a-b46f-11',_binary 'j¢_\Þ╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary '\±D$\Ù{oB¥Är╝1┐\Ò:\▀'),(_binary 'j└Ü\Û╝\Ú\±ïH×¢\Õë',15450.00,'BOOKED',_binary '\0',NULL,0,NULL,NULL,'2026-09-30 09:00:00.000000','20:00:00.000000',NULL,'VIRTUAL','2026-10-08','19:00:00.000000','Escalamiento de biorreactores','https://meet.jit.si/knowlink-clase-17',_binary ' ╩å\┬\ãM2╝║\ı<	\Ê~',_binary 'j┐m{╝\Ú\±ïH×¢\Õë',_binary '║\ãL]º;Eç¡j└\Îp\╩',_binary 'h\ÚHùØ_Eoü\═\Ù\ð}qø'),(_binary '▒@¥!I░│g<■V/óy',3605.00,'BOOKED',_binary '',NULL,0,NULL,NULL,'2026-09-19 21:40:18.079909','09:00:00.000000',NULL,'VIRTUAL','2026-09-21','08:00:00.000000','windows vs linux',NULL,_binary '63fd0f9b-b46f-11',_binary '6╗\Ë\¦\Ó\ÔJìÿ·▓\§╣A\Þ*',_binary '63fdb17c-b46f-11',_binary 'G┤r\±▓\ËH×¢\Õë');
/*!40000 ALTER TABLE `booking` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `booking_cancellation`
--

DROP TABLE IF EXISTS `booking_cancellation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_cancellation` (
  `cancellation_id` binary(16) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `cancelled_by` enum('STUDENT','TUTOR') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `hours_in_advance` bigint NOT NULL,
  `refund_destination` enum('STUDENT','TUTOR') NOT NULL,
  `refund_policy` enum('REFUND_TOTAL_STUDENT','TRANSFER_TOTAL_TUTOR') NOT NULL,
  `booking_id` binary(16) NOT NULL,
  PRIMARY KEY (`cancellation_id`),
  KEY `FKeyldgt56xypn4vfggjknbtfl4` (`booking_id`),
  CONSTRAINT `FKeyldgt56xypn4vfggjknbtfl4` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `booking_cancellation`
--

LOCK TABLES `booking_cancellation` WRITE;
/*!40000 ALTER TABLE `booking_cancellation` DISABLE KEYS */;
INSERT INTO `booking_cancellation` VALUES (_binary 'j╣ ╝\Ú\±ïH×¢\Õë',15450.00,'STUDENT','2026-09-07 10:00:00.000000',48,'STUDENT','REFUND_TOTAL_STUDENT',_binary 'jÀ\ÍK╝\Ú\±ïH×¢\Õë'),(_binary 'j╗ø;╝\Ú\±ïH×¢\Õë',15450.00,'TUTOR','2026-09-15 09:00:00.000000',24,'TUTOR','TRANSFER_TOTAL_TUTOR',_binary 'j║ƒä╝\Ú\±ïH×¢\Õë');
/*!40000 ALTER TABLE `booking_cancellation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `booking_hold`
--

DROP TABLE IF EXISTS `booking_hold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_hold` (
  `hold_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_time` time(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `start_time` time(6) NOT NULL,
  `student_id` binary(16) NOT NULL,
  `time_slot_id` binary(16) NOT NULL,
  PRIMARY KEY (`hold_id`),
  KEY `FK6868el78afh775jx1c9vdmtr3` (`student_id`),
  KEY `FK7ihwdagfm6rk7gp464tilj7vj` (`time_slot_id`),
  CONSTRAINT `FK6868el78afh775jx1c9vdmtr3` FOREIGN KEY (`student_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FK7ihwdagfm6rk7gp464tilj7vj` FOREIGN KEY (`time_slot_id`) REFERENCES `time_slot` (`time_slot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `booking_hold`
--

LOCK TABLES `booking_hold` WRITE;
/*!40000 ALTER TABLE `booking_hold` DISABLE KEYS */;
/*!40000 ALTER TABLE `booking_hold` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `career`
--

DROP TABLE IF EXISTS `career`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `career` (
  `career_id` binary(16) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`career_id`),
  UNIQUE KEY `UK4i26x57mopr9pseu6r3d1faia` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `career`
--

LOCK TABLES `career` WRITE;
/*!40000 ALTER TABLE `career` DISABLE KEYS */;
INSERT INTO `career` VALUES (_binary 'ï\╚\▄WOäCû┐eÅ\¶é\Ô','Arquitectura'),(_binary '..░½3cIïÿê-4\¤\╚u','Ciencias de la Computaci├│n'),(_binary '©\Úb\┘WìH\÷▒y┤è;¼','Comunicaci├│n Social'),(_binary '~©ö+Ooö┤\µ\µT;ca','Contador P├║blico'),(_binary 'Rü\Õ\n?\þAÏºkr¼É}','Derecho'),(_binary 'ıÇd\▄RCå\├8Ü\ßï','Dise├▒o Gr├ífico'),(_binary '\ß¢1*×qG.▓»dYglv','Enfermer├¡a'),(_binary '─»ƒ#\─cJ¨åæ▒c\ÕÜt','F├¡sica'),(_binary '\─\¸░è<OÌÂ Ví\Í\Ú\ð','Ingenier├¡a Civil'),(_binary '\÷^ÿüDoå\ÙÄo▒░╝}','Ingenier├¡a El├®ctrica'),(_binary '└ÅºiC²HE®«5ôQ$','Ingenier├¡a Electr├│nica'),(_binary 'É\ßw│G2áw S+¡\\¿','Ingenier├¡a en Sistemas'),(_binary '\ý\ýå\¾IyåÀJL?\¾S!','Ingenier├¡a Industrial'),(_binary 'Á\¯║A}SL£▓*Çv\╬','Ingenier├¡a Mec├ínica'),(_binary '\Ûƒ`+H,ì×\±í\Î╔ôö','Ingenier├¡a Qu├¡mica'),(_binary 'n\ÚªôG0í. 	,\0Y','Licenciatura en Administraci├│n'),(_binary '│å\¤\─fB║ûe\█aÆa|T','Matem├ítica'),(_binary 'V\­\­Å│nCÅT»Å\­½.E','Medicina'),(_binary '╣Àæø\¯MÂ«ª┐\Ú┴/%[','Otra'),(_binary 'Jo\ÙÂƒHö(/\▄oqL','Psicolog├¡a'),(_binary 'â©(\Õp	Jïò~jè\Î%Ä ','Qu├¡mica');
/*!40000 ALTER TABLE `career` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `claim_attachment`
--

DROP TABLE IF EXISTS `claim_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `claim_attachment` (
  `attachment_id` binary(16) NOT NULL,
  `content_type` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `size_in_bytes` bigint NOT NULL,
  `storage_path` varchar(500) NOT NULL,
  `claim_id` binary(16) NOT NULL,
  PRIMARY KEY (`attachment_id`),
  KEY `idx_claim_attachment_claim_id` (`claim_id`),
  CONSTRAINT `FK4nqf1i4v6s0xvfwaqpdi0q9w` FOREIGN KEY (`claim_id`) REFERENCES `session_claim` (`claim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `claim_attachment`
--

LOCK TABLES `claim_attachment` WRITE;
/*!40000 ALTER TABLE `claim_attachment` DISABLE KEYS */;
/*!40000 ALTER TABLE `claim_attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `funds_transfer`
--

DROP TABLE IF EXISTS `funds_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `funds_transfer` (
  `funds_transfer_id` binary(16) NOT NULL,
  `blocking_claim_id` binary(16) DEFAULT NULL,
  `concept` varchar(500) NOT NULL,
  `funds_status` enum('HELD','REFUNDED_TO_STUDENT','RELEASED_TO_TUTOR','SUSPENDED_BY_CLAIM') NOT NULL,
  `original_amount` decimal(10,2) NOT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `recipient` enum('STUDENT','TUTOR') NOT NULL,
  `system_retention_percentage` decimal(5,2) NOT NULL,
  `transferred_amount` decimal(10,2) NOT NULL,
  `booking_id` binary(16) NOT NULL,
  PRIMARY KEY (`funds_transfer_id`),
  UNIQUE KEY `uk_funds_transfer_booking` (`booking_id`),
  CONSTRAINT `FK8jd5cgykuspxi24qa5o46y1ta` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `funds_transfer`
--

LOCK TABLES `funds_transfer` WRITE;
/*!40000 ALTER TABLE `funds_transfer` DISABLE KEYS */;
/*!40000 ALTER TABLE `funds_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rating`
--

DROP TABLE IF EXISTS `rating`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rating` (
  `rating_id` binary(16) NOT NULL,
  `comment` text,
  `rating_date` datetime(6) NOT NULL,
  `score` int NOT NULL,
  `visible` bit(1) NOT NULL,
  `booking_id` binary(16) NOT NULL,
  `rated_user_id` binary(16) NOT NULL,
  `rater_user_id` binary(16) NOT NULL,
  PRIMARY KEY (`rating_id`),
  KEY `FKsxjfkf3kxooiimj82b64wmgrx` (`booking_id`),
  KEY `FKbr3n02cgdvvmo19cxwau1fknk` (`rated_user_id`),
  KEY `FKjpsjupqd68yttxexd310imdau` (`rater_user_id`),
  CONSTRAINT `FKbr3n02cgdvvmo19cxwau1fknk` FOREIGN KEY (`rated_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKjpsjupqd68yttxexd310imdau` FOREIGN KEY (`rater_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKsxjfkf3kxooiimj82b64wmgrx` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rating`
--

LOCK TABLES `rating` WRITE;
/*!40000 ALTER TABLE `rating` DISABLE KEYS */;
/*!40000 ALTER TABLE `rating` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `session_claim`
--

DROP TABLE IF EXISTS `session_claim`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `session_claim` (
  `claim_id` binary(16) NOT NULL,
  `active_key` varchar(100) DEFAULT NULL,
  `claimant_role` enum('ADMIN','STUDENT','TUTOR') NOT NULL,
  `comment` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `reason` enum('I_COULD_NOT_ATTEND','STUDENT_COULD_NOT_ATTEND') NOT NULL,
  `status` enum('OPEN','RESOLVED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `booking_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`claim_id`),
  UNIQUE KEY `uk_session_claim_active_key` (`active_key`),
  KEY `idx_session_claim_booking_id` (`booking_id`),
  KEY `idx_session_claim_user_status` (`user_id`,`status`),
  CONSTRAINT `FK71xibt3b7xvbwpyclvrcrf2x5` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKa43jgom2m1nsmal4ayp1tdbci` FOREIGN KEY (`booking_id`) REFERENCES `booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `session_claim`
--

LOCK TABLES `session_claim` WRITE;
/*!40000 ALTER TABLE `session_claim` DISABLE KEYS */;
/*!40000 ALTER TABLE `session_claim` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_profile`
--

DROP TABLE IF EXISTS `student_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_profile` (
  `student_profile_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `institutional_id` varchar(255) DEFAULT NULL,
  `profile_picture_url` varchar(2048) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `career_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`student_profile_id`),
  UNIQUE KEY `UK99mm2qc8gq78mojsjmdhqqrtd` (`user_id`),
  KEY `FKsgic1uxsltm5mdlaq3mh5m17m` (`career_id`),
  CONSTRAINT `FKh6555c9k0gv0yddac6llslk3t` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKsgic1uxsltm5mdlaq3mh5m17m` FOREIGN KEY (`career_id`) REFERENCES `career` (`career_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_profile`
--

LOCK TABLES `student_profile` WRITE;
/*!40000 ALTER TABLE `student_profile` DISABLE KEYS */;
INSERT INTO `student_profile` VALUES (_binary 'a#¬\ÝtïA@ÀÇ\Ë\ãg\0â','2026-09-30 16:07:02.696401','UNQ-2026-02',NULL,NULL,_binary '\Ûƒ`+H,ì×\±í\Î╔ôö',_binary 'äZ>░Gá\¦OP╠»[c'),(_binary 'l	.å┤r\±▓\ËH×¢\Õë','2026-09-19 18:38:15.000000',NULL,NULL,NULL,_binary 'É\ßw│G2áw S+¡\\¿',_binary '63fce54a-b46f-11'),(_binary 'l\n┤r\±▓\ËH×¢\Õë','2026-09-19 18:38:15.000000',NULL,NULL,NULL,_binary 'V\­\­Å│nCÅT»Å\­½.E',_binary '63fd0f9b-b46f-11'),(_binary 'ê\ãy_vJYƒ\ÔV\Ù#[','2026-09-30 16:07:15.669956','UNQ-2026-01',NULL,NULL,_binary '\Ûƒ`+H,ì×\±í\Î╔ôö',_binary ' ╩å\┬\ãM2╝║\ı<	\Ê~');
/*!40000 ALTER TABLE `student_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subjects`
--

DROP TABLE IF EXISTS `subjects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subjects` (
  `subject_id` binary(16) NOT NULL,
  `is_basic` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  `career_id` binary(16) NOT NULL,
  PRIMARY KEY (`subject_id`),
  UNIQUE KEY `UKaodt3utnw0lsov4k9ta88dbpr` (`name`),
  KEY `FK9ay6rjt9nij0xpff1rvvy631g` (`career_id`),
  CONSTRAINT `FK9ay6rjt9nij0xpff1rvvy631g` FOREIGN KEY (`career_id`) REFERENCES `career` (`career_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `subjects`
--

LOCK TABLES `subjects` WRITE;
/*!40000 ALTER TABLE `subjects` DISABLE KEYS */;
INSERT INTO `subjects` VALUES (_binary '=\õ\‗\Ì\ÒI\±¥5Â\ÈUGö',_binary '\0','Hormig├│n Armado',_binary '\─\¸░è<OÌÂ Ví\Í\Ú\ð'),(_binary 'XÇ\È\±rJãåIVÎæ©',_binary '\0','Algoritmos',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary 'Éce}#CËú│á¡Sé¿E',_binary '\0','Resistencia de Materiales',_binary '\─\¸░è<OÌÂ Ví\Í\Ú\ð'),(_binary '\¤qd¿\├N[åÑDø\­u\┌',_binary '','F├¡sica',_binary 'ï\╚\▄WOäCû┐eÅ\¶é\Ô'),(_binary 'E\õFHM╗/áj\¦\├%',_binary '','An├ílisis Matem├ítico',_binary 'ï\╚\▄WOäCû┐eÅ\¶é\Ô'),(_binary '\0ôkOÌ▒ªê\ı\ý+J«',_binary '\0','Sistemas Distribuidos',_binary '..░½3cIïÿê-4\¤\╚u'),(_binary '2û»\╚4ùL2ì	\ı,MX\Ì',_binary '\0','Auditor├¡a',_binary '~©ö+Ooö┤\µ\µT;ca'),(_binary '7£	SÊêLpìz▓o┴°┐',_binary '\0','Sistemas Operativos',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary '8+^ù\§Nø¨³W\╦Â',_binary '\0','Inteligencia Artificial',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary '?I}┴©\ÙD=ï@\Õíû;r',_binary '\0','Base de Datos',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary '?░\╔\0®Mü¡Ðò!\µ',_binary '\0','Gesti├│n de Proyectos',_binary 'n\ÚªôG0í. 	,\0Y'),(_binary 'A{º\═0¼JÑØî#-AC,',_binary '\0','Ciencia de los Materiales',_binary '\Ûƒ`+H,ì×\±í\Î╔ôö'),(_binary 'O\ýh¿\ÊIOñÂéÿ\\N¨ûú',_binary '','Ingl├®s',_binary 'ï\╚\▄WOäCû┐eÅ\¶é\Ô'),(_binary 'W\ðN\0╚ôKÃÿl5=ó\┌┼ú',_binary '\0','Machine Learning',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary 'f\¶7E\õJ\n╗/b|\┘)\¤',_binary '\0','Teor├¡a de la Computaci├│n',_binary '..░½3cIïÿê-4\¤\╚u'),(_binary 'k%Q¿cgG@Ö▓\È\¾J41',_binary '\0','Costos',_binary '~©ö+Ooö┤\µ\µT;ca'),(_binary 'kæ°³\÷ÜFÂá\╠U¨E:\▀',_binary '\0','Contabilidad',_binary 'n\ÚªôG0í. 	,\0Y'),(_binary 'àXfåmwK\rí┐\Ï$\Ã\Õ',_binary '\0','Est├ítica',_binary '\─\¸░è<OÌÂ Ví\Í\Ú\ð'),(_binary 'ê|\¾0\╚³HÉ│ó\õ¿ziä',_binary '\0','Ingenier├¡a de Software',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary 'Æ;õúÆ#Kı¬áwRÅÇ\╔\ã',_binary '\0','Finanzas Corporativas',_binary '~©ö+Ooö┤\µ\µT;ca'),(_binary 'ö W\═·>HYÅi)Ëä\ßM',_binary '\0','Redes',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary 'ƒ¬\Ì|ëdMn©?ƒ\"/W³',_binary '\0','Balances de Masa y Energ├¡a',_binary '\Ûƒ`+H,ì×\±í\Î╔ôö'),(_binary 'ª¥ñ{ÆL■┤ä\ızò1o\Ì',_binary '','Qu├¡mica',_binary 'ï\╚\▄WOäCû┐eÅ\¶é\Ô'),(_binary '▒¿\▄[mÂKåº[^┤■M\¯',_binary '\0','Topograf├¡a',_binary '\─\¸░è<OÌÂ Ví\Í\Ú\ð'),(_binary '▓ctdø▓BÃæVÆG■¨┤y',_binary '\0','Procesos Biotecnol├│gicos',_binary '\Ûƒ`+H,ì×\±í\Î╔ôö'),(_binary '└]Ö\¶K@CáØ<\Z	¥ø',_binary '\0','Econom├¡a',_binary 'n\ÚªôG0í. 	,\0Y'),(_binary '─┤RcåçM ®z\¦Hüûá',_binary '\0','Arquitectura de Computadoras',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary '╠¼/]\µH~Ç╗mØÜ`X',_binary '\0','Compiladores',_binary '..░½3cIïÿê-4\¤\╚u'),(_binary '\┘`\╠I\Ó	Ivëº\ãpë*c',_binary '\0','Programaci├│n',_binary 'É\ßw│G2áw S+¡\\¿'),(_binary '\¦%\ÔY\¸Cøô\Î\Z\§═Ü@\¦',_binary '','├ülgebra',_binary 'ï\╚\▄WOäCû┐eÅ\¶é\Ô'),(_binary '\Ò\Ô\¾:\­A¡ò\┘Xgî',_binary '\0','Enfoque Lean ├ügil',_binary 'n\ÚªôG0í. 	,\0Y'),(_binary '\Û\þ·âüI ×²pâg\╔\Ú',_binary '\0','Fisicoqu├¡mica',_binary '\Ûƒ`+H,ì×\±í\Î╔ôö'),(_binary '\‗m ·k\╬Lñáf\¯UÆ\ý\Õà',_binary '\0','Estructuras de Datos',_binary 'É\ßw│G2áw S+¡\\¿');
/*!40000 ALTER TABLE `subjects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `time_slot`
--

DROP TABLE IF EXISTS `time_slot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `time_slot` (
  `time_slot_id` binary(16) NOT NULL,
  `date` date NOT NULL,
  `end_time` time(6) NOT NULL,
  `start_time` time(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  `tutor_profile_id` binary(16) NOT NULL,
  `availability_block_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`time_slot_id`),
  KEY `FKb99nm0qhmp2sbni72g3gc3yg5` (`availability_block_id`),
  CONSTRAINT `FKb99nm0qhmp2sbni72g3gc3yg5` FOREIGN KEY (`availability_block_id`) REFERENCES `availability_block` (`availability_block_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `time_slot`
--

LOCK TABLES `time_slot` WRITE;
/*!40000 ALTER TABLE `time_slot` DISABLE KEYS */;
INSERT INTO `time_slot` VALUES (_binary '·»»=2L	ècá\ý}Ðäo','2026-09-23','17:00:00.000000','08:00:00.000000','AVAILABLE',_binary '	iÊ┤r\±▓\ËH×¢\Õë',_binary 'â\┘\ÊD\ÚB)à5&ÖuÇÀN'),(_binary 'Jé[\Ë\ãOêöà½┤äç\ÕY','2026-11-28','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary ';bW×¹qCë╗\Ã\¤#À3,«'),(_binary '\n p\ðL¢¿ú\┬\▀\Ò\╬\n\Ù','2026-12-19','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '¬\╠\'?\µBÊ║q|6>▒â'),(_binary '\nú\▄n×Aä¼HI\╬S\õ\ã','2026-10-10','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '½\'@e\┬@\¸£\┬/\‗?îF '),(_binary '\È\Õ@\"MJ\Óÿ çbT\¶/\Ý','2026-11-28','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'á\Ðl²Ø\ÐF_ùI¡R\¸\''),(_binary 'eI!\ÔºCº£\Þ(ç▒\Ú\¤Q','2026-10-03','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'ìG\Ë\0└║Gô║\ýåE\rgwd'),(_binary '#\‗`▓}ÆC[ÄçË¬jd$7','2026-12-12','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '&<8ƒSHÕïí\Z1»\═\¸C'),(_binary '/â╗\┼\ı@Dê?\┬,:øTÜ','2026-09-24','17:00:00.000000','08:00:00.000000','AVAILABLE',_binary '	iÊ┤r\±▓\ËH×¢\Õë',_binary 'é▒9ó YIZøh/Ä+¼\─\Ó'),(_binary '2}]Z\─Jvè(H┌Ä\╠\¯%','2026-11-14','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '\ãld\"■\├LßÂêüè&\Óƒ'),(_binary '6╗\Ë\¦\Ó\ÔJìÿ·▓\§╣A\Þ*','2026-09-21','17:00:00.000000','08:00:00.000000','AVAILABLE',_binary '	iÊ┤r\±▓\ËH×¢\Õë',_binary 'ÉóíühqMÑÙ│ÜCT|'),(_binary '>ÜnF\┼DL×\þàÉF`>','2026-10-24','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '9üïI╣F┬çl·N╠¢\▄8'),(_binary 'Hø\Ôæ\ÊrDÜúr¬6▓q','2026-12-05','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'fÂèg·IU¥Ü¹:4x\Ïm'),(_binary 'KuHÍº\┌Efù®uKø\"▓','2026-10-17','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '½wixLW£WkT[╗°Ü'),(_binary 'Y\ÙY\‗ÁJ|╣æ┐_N\±¬\╠','2026-11-07','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'i\▄M\╩r\\@¦ØP?4ïu\‗'),(_binary 'j¿® ╝\Ú\±ïH×¢\Õë','2026-09-03','19:00:00.000000','18:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j¿ƒ\´╝\Ú\±ïH×¢\Õë'),(_binary 'j¬╩ò╝\Ú\±ïH×¢\Õë','2026-09-05','11:00:00.000000','10:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j¬\ã═╝\Ú\±ïH×¢\Õë'),(_binary 'j¼-4╝\Ú\±ïH×¢\Õë','2026-09-08','20:00:00.000000','19:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j¼)^╝\Ú\±ïH×¢\Õë'),(_binary 'j¡U╝\Ú\±ïH×¢\Õë','2026-09-10','19:00:00.000000','18:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j¡¥╝\Ú\±ïH×¢\Õë'),(_binary 'j«	Z╝\Ú\±ïH×¢\Õë','2026-09-12','11:00:00.000000','10:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j«╩╝\Ú\±ïH×¢\Õë'),(_binary 'j» >╝\Ú\±ïH×¢\Õë','2026-09-15','20:00:00.000000','19:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j»H╝\Ú\±ïH×¢\Õë'),(_binary 'j░-²╝\Ú\±ïH×¢\Õë','2026-09-17','19:00:00.000000','18:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j░*b╝\Ú\±ïH×¢\Õë'),(_binary 'j▓\█\Ò╝\Ú\±ïH×¢\Õë','2026-09-19','11:00:00.000000','10:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j▓\Í|╝\Ú\±ïH×¢\Õë'),(_binary 'j│\Ï█╝\Ú\±ïH×¢\Õë','2026-09-22','20:00:00.000000','19:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j│\ı:╝\Ú\±ïH×¢\Õë'),(_binary 'j┤┼¡╝\Ú\±ïH×¢\Õë','2026-09-24','19:00:00.000000','18:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j┤┴\§╝\Ú\±ïH×¢\Õë'),(_binary 'jÁ\ß\'╝\Ú\±ïH×¢\Õë','2026-09-26','11:00:00.000000','10:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'jÁ\▄╔╝\Ú\±ïH×¢\Õë'),(_binary 'jÂ\Ó└╝\Ú\±ïH×¢\Õë','2026-09-29','20:00:00.000000','19:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'jÂ\¦.╝\Ú\±ïH×¢\Õë'),(_binary 'jÀ\Ð■╝\Ú\±ïH×¢\Õë','2026-09-09','19:00:00.000000','18:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'jÀ\╬╝\Ú\±ïH×¢\Õë'),(_binary 'j║£╝\Ú\±ïH×¢\Õë','2026-09-16','19:00:00.000000','18:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j║ÿC╝\Ú\±ïH×¢\Õë'),(_binary 'j╗\Ý▀╝\Ú\±ïH×¢\Õë','2026-09-23','19:00:00.000000','18:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j╗\Û\\╝\Ú\±ïH×¢\Õë'),(_binary 'j¢_\Þ╝\Ú\±ïH×¢\Õë','2026-10-01','20:00:00.000000','19:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j¢X╔╝\Ú\±ïH×¢\Õë'),(_binary 'j┐m{╝\Ú\±ïH×¢\Õë','2026-10-08','20:00:00.000000','19:00:00.000000','AVAILABLE',_binary '(\r└\"6MÃê╠ôT░u;(',_binary 'j┐O¦╝\Ú\±ïH×¢\Õë'),(_binary 'qªÑt\█Jã¢P\"¥Ñ°¹','2026-10-17','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '\Ó2)║~rOÇÀ*\"óÑ\Ý\Z3'),(_binary 'u\Ã\Òc$A\"╣·³xôuÇ\¾','2026-12-05','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '\┌nQó\╬A\Ò¼\╚\═V³│Ø'),(_binary '}▓\Ì\¾G\ÙGòá┐1BC«wì','2026-10-10','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 's#w▓N\ÓGÌïr|Àwmº'),(_binary 'â▀û\Í\¶LK▒┴`,î\╦\õ\┘','2026-11-07','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '\r2¼?DC©ïkê\÷\¶▄Ü\µ'),(_binary 'è*ø};íOºüÈÖ\Ê2î┤z','2026-09-22','17:00:00.000000','08:00:00.000000','AVAILABLE',_binary '	iÊ┤r\±▓\ËH×¢\Õë',_binary '\¸\Ô%RÑM\\¥ôRÆ\§\¤\Ã'),(_binary 'ù_|î(²O\­ƒ\Ì■\ßÁ7','2026-11-21','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'ó║s\¸î\ÈFn»╝\'ðÆnz\´'),(_binary 'Âêè\ı\ðH7│áÆ \Ì\µj','2026-09-20','14:30:00.000000','08:00:00.000000','AVAILABLE',_binary '	iÊ┤r\±▓\ËH×¢\Õë',_binary '\ý}\▄r\÷1I─┐v▄╝ \█b'),(_binary '╣ñæ:LàMOÿU\Ý\─5H\┌','2026-10-31','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'Eâ5LJ░\Ì\´\¯ù 0'),(_binary '¥\╩\Ê\Ó\¾IláCG\ð¨Æ','2026-12-26','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '³1ô\┼■×F░╣¹g=Bêº'),(_binary '\├\Ò■H!N\ÔÁZ2\Ù×\ÙT','2026-09-20','23:00:00.000000','20:00:00.000000','AVAILABLE',_binary '	iÊ┤r\±▓\ËH×¢\Õë',_binary 'SáQoK4¡\┌0î»ô\Î\¤'),(_binary '├│¹Ñƒ╗CÜg=lñ\╦g','2026-11-21','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '\¯\═\┬\ýtL╗Æ\╩\Z7ð©g'),(_binary 'Ã½)@HøAôÑ┐¬\─','2026-12-19','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '³\ÊÃÀj▓LÃú:)[1©'),(_binary '\ÎmvQrôDâÜCºéd#','2026-12-26','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'ú8Ä!\"Nb¥gy««_\µ'),(_binary 'Ï▒oíaK¡\┘┼âFî\þ','2026-10-24','12:00:00.000000','09:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'k»CC@╚Éz:6\╔~-N'),(_binary '\█A┐&)nM\\ì┴ièÇ\─Xn','2026-12-12','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'Ö[\þ_äI/ªr| »$\n'),(_binary '\ý+à(\'\ËJ.®ÿ0r■& ','2026-10-03','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary '!╗\ßz\÷!Lñ│░\¦*(j'),(_binary '\´\¸ª©JÉJåÀ\─oÉB\ß\Û','2026-10-31','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'J\Ù┘Éæ¢BÃ×┴■\╩lIm'),(_binary '\¶\╦\█2╝gE\Zæ\÷~\¦Ç','2026-09-20','23:00:00.000000','19:00:00.000000','AVAILABLE',_binary '	\0î┤r\±▓\ËH×¢\Õë',_binary 'éaT&\0DÉú╣}Â┴\Ðz'),(_binary '■\ÌkmoK¬©X║Ì¡Ä»','2026-11-14','17:30:00.000000','14:00:00.000000','AVAILABLE',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^',_binary 'D\ÞUö\Ò{DdÆónä\Þ\'\ý');
/*!40000 ALTER TABLE `time_slot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `token_blacklist`
--

DROP TABLE IF EXISTS `token_blacklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `token_blacklist` (
  `token_blacklist_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `jti` varchar(255) NOT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`token_blacklist_id`),
  UNIQUE KEY `UK479yp5jc6091k9yltm2as3pgf` (`jti`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `token_blacklist`
--

LOCK TABLES `token_blacklist` WRITE;
/*!40000 ALTER TABLE `token_blacklist` DISABLE KEYS */;
/*!40000 ALTER TABLE `token_blacklist` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tokens`
--

DROP TABLE IF EXISTS `tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tokens` (
  `token_id` binary(16) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `token_expiration_date` datetime(6) NOT NULL,
  `user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`token_id`),
  KEY `FK2dylsfo39lgjyqml2tbe0b0ss` (`user_id`),
  CONSTRAINT `FK2dylsfo39lgjyqml2tbe0b0ss` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tokens`
--

LOCK TABLES `tokens` WRITE;
/*!40000 ALTER TABLE `tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tutor_profile`
--

DROP TABLE IF EXISTS `tutor_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tutor_profile` (
  `tutor_profile_id` binary(16) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `average_rating` double DEFAULT NULL,
  `biography` text,
  `created_at` datetime(6) NOT NULL,
  `institutional_id` varchar(255) DEFAULT NULL,
  `mercado_pago_linked` bit(1) NOT NULL,
  `min_notice_minutes` int DEFAULT NULL,
  `profile_picture_url` varchar(2048) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `verified` bit(1) NOT NULL,
  `career_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`tutor_profile_id`),
  UNIQUE KEY `UKrst6kajqljg8j4wgk999u7bfm` (`user_id`),
  KEY `FK9t2fvptionn9qbyd1936qea46` (`career_id`),
  CONSTRAINT `FK9t2fvptionn9qbyd1936qea46` FOREIGN KEY (`career_id`) REFERENCES `career` (`career_id`),
  CONSTRAINT `FKh2whb32k13etikxo99v2gtvdy` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tutor_profile`
--

LOCK TABLES `tutor_profile` WRITE;
/*!40000 ALTER TABLE `tutor_profile` DISABLE KEYS */;
INSERT INTO `tutor_profile` VALUES (_binary '╩┤r\±▓\ËH×¢\Õë',NULL,NULL,'Profesor de programacion y bases de datos','2026-09-19 18:35:32.000000',NULL,_binary '\0',NULL,NULL,NULL,_binary '\0',_binary 'É\ßw│G2áw S+¡\\¿',_binary '63fdac46-b46f-11'),(_binary '	\0î┤r\±▓\ËH×¢\Õë',NULL,NULL,'Especialista en algoritmos y estructuras de datos','2026-09-19 18:35:32.000000',NULL,_binary '\0',NULL,NULL,NULL,_binary '\0',_binary '..░½3cIïÿê-4\¤\╚u',_binary '63fdb05b-b46f-11'),(_binary '	iÊ┤r\±▓\ËH×¢\Õë',NULL,NULL,'Docente de inteligencia artificial y machine learning','2026-09-19 18:35:32.000000',NULL,_binary '\0',NULL,NULL,NULL,_binary '\0',_binary 'É\ßw│G2áw S+¡\\¿',_binary '63fdb17c-b46f-11'),(_binary '\'o57\÷G$«\¾Ö0\▄m¢',NULL,NULL,'Soy estudiante avanzado de ingenier├¡a en qu├¡mica','2026-09-30 15:50:59.890112','12345',_binary '\0',NULL,NULL,NULL,_binary '\0',_binary '\Ûƒ`+H,ì×\±í\Î╔ôö',_binary '*h\ÚW└\╦G│Çód5\'\±ÜÄ'),(_binary '(\r└\"6MÃê╠ôT░u;(','Av. Siempreviva 742, CABA',NULL,'Ingeniera qu├¡mica con 6 a├▒os de docencia en balances, procesos y fisicoqu├¡mica.','2026-09-30 15:11:55.816108','UNQ-IngQuim-2026',_binary '\0',NULL,NULL,NULL,_binary '\0',_binary '\Ûƒ`+H,ì×\±í\Î╔ôö',_binary '║\ãL]º;Eç¡j└\Îp\╩'),(_binary '¤│æO\¾\═NE¢|¥\¸]\±i^','Biblioteca',NULL,'Soy ingeniera qu├¡mica, con 10 a├▒os de experiencia.  ','2026-09-30 19:42:24.313117','12345',_binary '\0',NULL,NULL,NULL,_binary '\0',_binary '\Ûƒ`+H,ì×\±í\Î╔ôö',_binary '\ÕR¿┤¬┤L┴╝³║P/ &');
/*!40000 ALTER TABLE `tutor_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tutor_subject`
--

DROP TABLE IF EXISTS `tutor_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tutor_subject` (
  `tutor_subject_id` binary(16) NOT NULL,
  `compensation_type` enum('FREE','PAID') NOT NULL,
  `description` text,
  `modality` enum('BOTH','IN_PERSON','VIRTUAL') NOT NULL,
  `price_per_hour` decimal(10,2) DEFAULT NULL,
  `tutor_subject_status` enum('ACTIVE','PENDING','REJECTED') NOT NULL,
  `subject_id` binary(16) NOT NULL,
  `tutor_profile_id` binary(16) NOT NULL,
  PRIMARY KEY (`tutor_subject_id`),
  KEY `FKwhpfca3wn38d0gwwt5ue4xqt` (`subject_id`),
  KEY `FK7gcc4ub2ji8gf45l6ygecvkx6` (`tutor_profile_id`),
  CONSTRAINT `FK7gcc4ub2ji8gf45l6ygecvkx6` FOREIGN KEY (`tutor_profile_id`) REFERENCES `tutor_profile` (`tutor_profile_id`),
  CONSTRAINT `FKwhpfca3wn38d0gwwt5ue4xqt` FOREIGN KEY (`subject_id`) REFERENCES `subjects` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tutor_subject`
--

LOCK TABLES `tutor_subject` WRITE;
/*!40000 ALTER TABLE `tutor_subject` DISABLE KEYS */;
INSERT INTO `tutor_subject` VALUES (_binary '\n!&┤r\±▓\ËH×¢\Õë','PAID','Clases de Base de Datos','BOTH',2500.00,'ACTIVE',_binary '?I}┴©\ÙD=ï@\Õíû;r',_binary '╩┤r\±▓\ËH×¢\Õë'),(_binary '\n*┤r\±▓\ËH×¢\Õë','PAID','Clases de Programaci├│n','BOTH',2500.00,'ACTIVE',_binary '\┘`\╠I\Ó	Ivëº\ãpë*c',_binary '╩┤r\±▓\ËH×¢\Õë'),(_binary '\n+q┤r\±▓\ËH×¢\Õë','PAID','Clases de Estructuras de Datos','BOTH',2500.00,'ACTIVE',_binary '\‗m ·k\╬Lñáf\¯UÆ\ý\Õà',_binary '╩┤r\±▓\ËH×¢\Õë'),(_binary '\nÑ┤r\±▓\ËH×¢\Õë','PAID','Clases de Teor├¡a de la Computaci├│n','VIRTUAL',3000.00,'ACTIVE',_binary 'f\¶7E\õJ\n╗/b|\┘)\¤',_binary '	\0î┤r\±▓\ËH×¢\Õë'),(_binary '\n¬┤r\±▓\ËH×¢\Õë','PAID','Clases de Compiladores','VIRTUAL',3000.00,'ACTIVE',_binary '╠¼/]\µH~Ç╗mØÜ`X',_binary '	\0î┤r\±▓\ËH×¢\Õë'),(_binary 'G┤r\±▓\ËH×¢\Õë','PAID','Clases de Sistemas Operativos','BOTH',3500.00,'ACTIVE',_binary '7£	SÊêLpìz▓o┴°┐',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '	ë┤r\±▓\ËH×¢\Õë','PAID','Clases de Inteligencia Artificial','BOTH',3500.00,'ACTIVE',_binary '8+^ù\§Nø¨³W\╦Â',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '\n╩┤r\±▓\ËH×¢\Õë','PAID','Clases de Machine Learning','BOTH',3500.00,'ACTIVE',_binary 'W\ðN\0╚ôKÃÿl5=ó\┌┼ú',_binary '	iÊ┤r\±▓\ËH×¢\Õë'),(_binary '1▓\Û2êäFlæ\'\Ì\┘\\','PAID',NULL,'VIRTUAL',15000.00,'PENDING',_binary 'A{º\═0¼JÑØî#-AC,',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'X\r©9?~Gô¿9\═Qu\Ï\Þ','PAID',NULL,'VIRTUAL',15000.00,'PENDING',_binary '\Û\þ·âüI ×²pâg\╔\Ú',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'h\ÚHùØ_Eoü\═\Ù\ð}qø','PAID',NULL,'VIRTUAL',15000.00,'PENDING',_binary '▓ctdø▓BÃæVÆG■¨┤y',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary 'ní\'R¢\rG║╗\n-ÌØ³ô','PAID',NULL,'VIRTUAL',100000.00,'PENDING',_binary 'A{º\═0¼JÑØî#-AC,',_binary '\'o57\÷G$«\¾Ö0\▄m¢'),(_binary '▒æH,└\╬@┤ñ0\Ôc×ö*g','PAID',NULL,'VIRTUAL',10000.00,'PENDING',_binary 'ƒ¬\Ì|ëdMn©?ƒ\"/W³',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '┴^\¤y\Z9Jk©\¶■£S\Õ\õ:','FREE',NULL,'IN_PERSON',NULL,'PENDING',_binary 'E\õFHM╗/áj\¦\├%',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^'),(_binary '\±D$\Ù{oB¥Är╝1┐\Ò:\▀','PAID',NULL,'VIRTUAL',15000.00,'PENDING',_binary 'ƒ¬\Ì|ëdMn©?ƒ\"/W³',_binary '(\r└\"6MÃê╠ôT░u;('),(_binary '\§|ÉdÂ\┬N\‗Ä\ßWmà\ði½','PAID',NULL,'VIRTUAL',10000.00,'PENDING',_binary 'ª¥ñ{ÆL■┤ä\ızò1o\Ì',_binary '¤│æO\¾\═NE¢|¥\¸]\±i^');
/*!40000 ALTER TABLE `tutor_subject` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` binary(16) NOT NULL,
  `account_status` enum('ACTIVE','DELETED','INACTIVE') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `dni` varchar(255) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `full_name` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `profile_picture_url` varchar(2048) DEFAULT NULL,
  `role` enum('ADMIN','STUDENT','TUTOR') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK6aphui3g30h49muho4c91n0yl` (`dni`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (_binary '*h\ÚW└\╦G│Çód5\'\±ÜÄ','ACTIVE','2026-09-30 15:50:59.839325',NULL,'40751682','julianls783@gmail.com','Juli├ín L├│pez Salvucci','$2a$10$Nm/q2uBV/sSnD5QML5mLyOuN441xLlEbUfsyHcK3tASuT4.4Pfraa','+543533501557',NULL,'TUTOR','2026-09-30 15:52:02.652281'),(_binary '63fce54a-b46f-11','ACTIVE','2026-09-19 18:16:33.000000',NULL,'40123456','juan.perez@test.com','Juan Perez','$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6','1155551111',NULL,'STUDENT',NULL),(_binary '63fd0f9b-b46f-11','ACTIVE','2026-09-19 18:16:33.000000',NULL,'41234567','maria.garcia@test.com','Maria Garcia','$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6','1155552222',NULL,'STUDENT',NULL),(_binary '63fdac46-b46f-11','ACTIVE','2026-09-19 18:16:33.000000',NULL,'38234567','carlos.lopez@test.com','Carlos Lopez','$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6','1155553333',NULL,'TUTOR',NULL),(_binary '63fdb05b-b46f-11','ACTIVE','2026-09-19 18:16:33.000000',NULL,'39345678','ana.martinez@test.com','Ana Martinez','$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6','1155554444',NULL,'TUTOR',NULL),(_binary '63fdb17c-b46f-11','ACTIVE','2026-09-19 18:16:33.000000',NULL,'37456789','pedro.fernandez@test.com','Pedro Fernandez','$2a$10$mIlh0.lvtcVeoAM/TX4nQeeL7NvVzaEMmdy/CtdszS8bMvH.7n7V6','1155555555',NULL,'TUTOR',NULL),(_binary 'äZ>░Gá\¦OP╠»[c','ACTIVE','2026-09-30 16:07:02.665117',NULL,'40987123','diego.rios@test.com','Diego R├¡os','$2a$10$r7ik367p98/jNW5TFWDCS.1I.lSAU6/BxlFdEJ0pv4Pnuq3GKiqgi','+541155552222',NULL,'STUDENT','2026-09-30 16:07:47.775639'),(_binary '║\ãL]º;Eç¡j└\Îp\╩','ACTIVE','2026-09-30 15:11:55.769267',NULL,'38654219','lucia.fernandez@test.com','Luc├¡a Fern├índez','$2a$10$u7m2TaJ.kHz1mgvQFJlOxO3h2ExuEXqw.akRTVCdec.5NZMwrOgFS','+541155551234',NULL,'TUTOR','2026-09-30 15:13:44.835143'),(_binary '\ÕR¿┤¬┤L┴╝³║P/ &','ACTIVE','2026-09-30 19:42:24.148808',NULL,'40751683','test04.t04@gmail.com','Paula Cabrera','$2a$10$d1cjN8HVW.l54LKT5UTjmuI/QeTW7CG95EQX0rZMIcuwFJuv1ynNO','+543533501557',NULL,'TUTOR','2026-09-30 19:44:42.152088'),(_binary ' ╩å\┬\ãM2╝║\ı<	\Ê~','ACTIVE','2026-09-30 16:07:15.638741',NULL,'41765432','sofia.gomez@test.com','Sof├¡a G├│mez','$2a$10$zW0XEXYiFBN87yD0KY9LgezfMmMrUqKtoL0b/RJMPdkDUxv9W4zTy','+541155551111',NULL,'STUDENT','2026-09-30 16:07:47.853774');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'knowlink_db'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-02 22:24:16
