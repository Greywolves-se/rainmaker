-- The demo's fictional offers (#149). Every company is made up, like the town of Vintra on the
-- test site. No offers for "health" and "personal-finance": those articles are yellow, and yellow
-- gets no suggestions in the demo. Two travel offers, so ranking by fit and CPC can be shown.
INSERT INTO offer (offer_id, advertiser, title, offer_text, category, cpc_ore) VALUES
    ('01a1629f-c401-79e3-9779-9e3779b10001', 'Nordsol Resor',
     'Höstresa till Mallorca – sju nätter', 'Flyg och hotell med frukost, avresa från Vintra.',
     'travel', 600),
    ('01a1629f-c402-73c6-aef3-3c6ef3620002', 'Vintra Resebyrå',
     'Weekend i Barcelona', 'Tre dagar med hotell i centrum och guidad stadsvandring.',
     'travel', 450),
    ('01a1629f-c403-7daa-b66d-daa66d130003', 'Fjällbutiken Vintra',
     'Vandringskängor för hösten', 'Prova i butiken och gå hem med ett par som sitter rätt.',
     'outdoor', 350),
    ('01a1629f-c404-778d-8de6-78dde6c40004', 'Vintra IF',
     'Säsongskort till Superettan 2027', 'Alla hemmamatcher på Älvvallen, med egen plats.',
     'sport', 300),
    ('01a1629f-c405-7171-9560-171560750005', 'Vintra stadsteater',
     'Biljetter till Ljus över älven', 'Vinterns stora musikal, med rabatt för studenter.',
     'culture', 250),
    ('01a1629f-c406-7b54-acda-b54cda260006', 'Skogens Skafferi',
     'Svampkurs i Vintraskogen', 'En dag med guide, korg och lunch över öppen eld.',
     'food', 300),
    ('01a1629f-c407-7538-b453-538453d70007', 'Vintra Kakel & Bad',
     'Gratis hembesök för badrumsoffert', 'Vi mäter upp och ger fast pris inom en vecka.',
     'home', 450),
    ('01a1629f-c408-7f1b-8bcd-f1bbcd880008', 'Älvdalens Bil',
     'Provkör en elbil i en helg', 'Hämta bilen på fredagen och lämna den på måndagen.',
     'cars', 500);
