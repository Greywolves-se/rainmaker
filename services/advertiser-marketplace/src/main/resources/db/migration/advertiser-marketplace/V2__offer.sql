-- Offers (#149, contract 3.4). An offer's destination is its own landing page in G3
-- (/landing/<offer_id>); the address is built from the app's settings, so it is not stored here.
-- category is a commercial category key (#145, D-3); display names come from translation files.
-- "other" is never an offer's category. cpc_ore is the price per click in whole öre.
CREATE TABLE offer (
    offer_id   uuid PRIMARY KEY,
    advertiser text NOT NULL CHECK (advertiser <> ''),
    title      text NOT NULL CHECK (title <> ''),
    offer_text text NOT NULL CHECK (offer_text <> ''),
    category   text NOT NULL CHECK (category IN ('travel', 'outdoor', 'sport', 'culture', 'food',
                                                 'home', 'cars', 'health', 'personal-finance')),
    cpc_ore    integer NOT NULL CHECK (cpc_ore > 0)
);
