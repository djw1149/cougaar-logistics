delete from header where nsn = '2540004072610';
delete from header where nsn = '2610002628677';
delete from header where nsn = '2815012148820';
delete from header where nsn = '2590012687208';
delete from header where nsn = '2835012691234';
delete from header where nsn = '2520013259834';
delete from header where nsn = '2610011714746';
delete from header where nsn = '2540011623834';
delete from header where nsn = '6150013101829';
delete from header where nsn = '8340001347512';
delete from header where nsn = '2520010870156';
delete from header where nsn = '2520011202169';
delete from army_spares_dcr_by_optempo where mei_nsn ='2320000771616' and part_nsn = '2610002628677';
delete from army_spares_dcr_by_optempo where mei_nsn ='2320000771617' and part_nsn = '2815012148820';
delete from army_spares_dcr_by_optempo where mei_nsn ='2320000771617' and part_nsn = '2540004072610';
delete from army_spares_dcr_by_optempo where mei_nsn ='2320000771617' and part_nsn = '2610002628677';
delete from army_spares_dcr_by_optempo where mei_nsn ='2350000566808' and part_nsn = '2540011623834';
delete from army_spares_dcr_by_optempo where mei_nsn ='2350000566808' and part_nsn = '2520010870156';
delete from army_spares_dcr_by_optempo where mei_nsn ='2350000566808' and part_nsn = '2520011202169';
delete from army_spares_dcr_by_optempo where mei_nsn ='2350000566808' and part_nsn = '8340001347512';
delete from army_spares_dcr_by_optempo where mei_nsn ='2350000566808' and part_nsn = '6150013101829';
delete from army_spares_dcr_by_optempo where mei_nsn ='2320011077155' and part_nsn = '2610011714746';

insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2540004072610', 'SEAT,VEHICULAR', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 140.0, 12.731, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2610002628677', 'TIRE,PNEUMATIC,VEHICULAR', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 93.0, 9.300, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2815012148820', 'ENGINEWITH', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 3086.0, 95.318, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2590012687208', 'PRE-CLEANERASSEMBLY', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 95.3, 14.131, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2835012691234', 'ENGINEMODULEFORWARD', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1155.0, 52.486, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2520013259834', 'TRANSMISSION,HYDRAULIC', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 6036.0, 157.923, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2610011714746', 'TIRE,PNEUMATIC,VEHICULAR', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 55.0, 8.696, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2540011623834', 'HEATER,VEHICULAR,COMPARTMEN', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 47.5, 5.452, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '6150013101829', 'CABLEASSEMBLY,POWER,', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 70.0, 3.941, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '8340001347512', 'TENT', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 135.0, 7.860, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2520010870156', 'TRANSFERTRANSMISSION', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 310.0, 26.5, NULL, NULL);
insert into header (COMMODITY,NSN,NOMENCLATURE,UI,SSC,PRICE,ICC,ALT,PLT,PCM,BOQ,DIQ,IAQ,NSO,QFD,ROP,OWRMRP,WEIGHT,CUBE,AAC,SLQ) values (NULL, '2520011202169', 'TRANSMISSION,HYDRAULIC,', 'EA', NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 379.0, 15.686, NULL, NULL);

insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2320000771616', 0.04, '2610002628677', 'HIGH', 'X40009', 'M35A2-1616');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2320000771617', 0.04, '2815012148820', 'HIGH', 'X40146', 'M35A2-1617');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2320000771617', 0.04, '2540004072610', 'HIGH', 'X40146', 'M35A2-1617');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2320000771617', 0.36, '2610002628677', 'HIGH', 'X40146', 'M35A2-1617');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2350000566808', 0.08, '2540011623834', 'HIGH', 'D11538', 'M577A2');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2350000566808', 0.08, '2520010870156', 'HIGH', 'D11538', 'M577A2');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2350000566808', 0.08, '2520011202169', 'HIGH', 'D11538', 'M577A2');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2350000566808', 0.24, '8340001347512', 'HIGH', 'D11538', 'M577A2');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2350000566808', 0.36, '6150013101829', 'HIGH', 'D11538', 'M577A2');
insert into army_spares_dcr_by_optempo (MEI_NSN, DCR, PART_NSN, OPTEMPO, LIN, MEI_NAME) values ('2320011077155', 0.04, '2610011714746', 'HIGH', 'T61494', 'M998');