-- More fictional offers for the demo (#260), so a green article can get up to three suggestions
-- and the editor sees a ranking: three each for travel, outdoor, sport, food and home, two for
-- culture and cars. Within a category the CPCs differ, so the order shows that CPC breaks ties.
-- Still none for "health" and "personal-finance": those articles are yellow (contract 3.3, D-4).
-- Every company is made up, like those in V3.
INSERT INTO offer (offer_id, advertiser, title, offer_text, category, cpc_ore) VALUES
    ('01a1629f-c409-78ff-b478-5799e5cb0009', 'Älvtåg',
     'Nattåg till fjällen – sovvagn för två', 'Somna i Vintra och vakna bland fjällen, frukost ingår.',
     'travel', 350),
    ('01a1629f-c40a-72e2-ac13-ef8e8d8d000a', 'Vintra Friluft',
     'Hyr tältpaket för höstturen', 'Tält, liggunderlag och sovsäckar för en helg, klart att hämta.',
     'outdoor', 300),
    ('01a1629f-c40b-7cc6-a3af-8783354e000b', 'Vintra Fjällguider',
     'Guidad vandring i Vintrafjällen', 'Två dagar med guide, stuga och all mat under turen.',
     'outdoor', 400),
    ('01a1629f-c40c-76a9-9b4b-1f77dd0f000c', 'Löparbutiken Vintra',
     'Löparskor med analys av steget', 'Vi filmar ditt löpsteg och hjälper dig välja rätt sko.',
     'sport', 250),
    ('01a1629f-c40d-708d-92e6-b76c84d1000d', 'Vintraloppet',
     'Tidig anmälan till Vintraloppet 2027', 'Startnummer och medalj till halva priset fram till jul.',
     'sport', 200),
    ('01a1629f-c40e-7a70-8a82-4f612c92000e', 'Vintra konsthall',
     'Årskort till konsthallen', 'Alla utställningar och visningar under ett helt år.',
     'culture', 150),
    ('01a1629f-c40f-7454-821d-e755d453000f', 'Matkassen Vintra',
     'Matkasse med lokala råvaror', 'Tre middagar för fyra, levererade hem på söndagen.',
     'food', 400),
    ('01a1629f-c410-7e37-b9b9-7f4a7c150010', 'Bageriet vid älven',
     'Surdegskurs en lördag', 'Baka ditt eget bröd och gå hem med en egen surdeg.',
     'food', 200),
    ('01a1629f-c411-781a-b155-173f23d60011', 'Rörmokarna i Vintra',
     'Ny snålspolande blandare', 'Vi monterar inom en vecka, till fast pris.',
     'home', 350),
    ('01a1629f-c412-71fe-a8f0-af33cb970012', 'Vintra Bygghandel',
     'Allt till badrumsrenoveringen', 'Kakel, tätskikt och verktyg på ett ställe, med rådgivning.',
     'home', 300),
    ('01a1629f-c413-7be1-a08c-472873580013', 'Vintra Däck',
     'Vinterdäck med fri montering', 'Boka tid före den första snön och få förvaring på köpet.',
     'cars', 300);
