-- Dimension: where deals happen. Codes match the app's Channel enum.
CREATE OR REPLACE VIEW dim_channel AS
SELECT *
FROM (
    VALUES
        ('CARD_SHOW',            'Card show',            'in person'),
        ('FACEBOOK_MARKETPLACE', 'Facebook Marketplace', 'online'),
        ('INSTAGRAM',            'Instagram',            'online'),
        ('EBAY',                 'eBay',                 'online'),
        ('WEBSITE',              'Website',              'online'),
        ('BUYLIST',              'Buylist',              'online'),
        ('OTHER',                'Other',                'other')
) AS t (channel, channel_name, channel_type)
