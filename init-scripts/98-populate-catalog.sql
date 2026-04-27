
-- Insert some initial popular catalog products to give the AI context before users start scanning receipts.
INSERT INTO p2p_product_catalog (id, generic_name, specific_name, brand, category, estimated_price, purchase_count) VALUES
    (gen_random_uuid(), 'Lapte', 'Lapte Zuzu 1.5% 1L', 'Zuzu', 'Lactate', 7.50, 150),
    (gen_random_uuid(), 'Paine', 'Paine feliata alba 500g', 'Vel Pitar', 'Panificatie', 6.00, 230),
    (gen_random_uuid(), 'Oua', 'Oua M 10 buc', 'Toneli', 'Alimente de baza', 11.50, 180),
    (gen_random_uuid(), 'Unt', 'Unt 82% 200g', 'President', 'Lactate', 15.00, 120),
    (gen_random_uuid(), 'Apa plata', 'Apa minerala plata 2L', 'Borsec', 'Bauturi', 3.50, 400),
    (gen_random_uuid(), 'Iaurt', 'Iaurt natur 3.5% 140g', 'Danone', 'Lactate', 2.50, 145),
    (gen_random_uuid(), 'Cafea', 'Cafea macinata 250g', 'Jacobs', 'Cafea si Ceai', 18.00, 90),
    (gen_random_uuid(), 'Zahar', 'Zahar alb 1kg', 'Margaritar', 'Alimente de baza', 5.50, 85),
    (gen_random_uuid(), 'Faina', 'Faina alba tip 000 1kg', 'Baneasa', 'Alimente de baza', 4.50, 110),
    (gen_random_uuid(), 'Ulei', 'Ulei de floarea soarelui 1L', 'Floriol', 'Alimente de baza', 9.00, 160),
    (gen_random_uuid(), 'Piept pui', 'Piept de pui dezosat', 'Agricola', 'Carne', 25.00, 75),
    (gen_random_uuid(), 'Detergent vase', 'Detergent capsule 40 buc', 'Fairy', 'Curatenie', 45.00, 60),
    (gen_random_uuid(), 'Hartie igienica', 'Hartie igienica 3 straturi 10 role', 'Zewa', 'Ingrijire personala', 20.00, 130),
    (gen_random_uuid(), 'Rosii', 'Rosii calitatea I', 'Fara brand', 'Legume', 8.00, 210),
    (gen_random_uuid(), 'Mere', 'Mere romanesti', 'Fara brand', 'Fructe', 4.50, 190);
