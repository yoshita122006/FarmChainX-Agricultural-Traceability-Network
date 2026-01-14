import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class FarmerAIService {
  
  // ⚠️ PUT YOUR GROQ API KEY HERE
  private readonly GROQ_API_KEY = 'YOUR_GROQ_API_KEY_HERE';
  private readonly GROQ_API_URL = 'https://api.groq.com/openai/v1/chat/completions';
  
  constructor(private http: HttpClient) { }
  
  getFarmingGuide(cropName: string): Observable<any> {
    console.log('🌾 Requesting AI farming guide for:', cropName);
    
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${this.GROQ_API_KEY}`,
      'Content-Type': 'application/json'
    });
    
    // Comprehensive prompt for farmer-specific information
    const prompt = this.createFarmerPrompt(cropName);
    
    const body = {
      model: 'llama-3.1-8b-instant', // Fast and free
      messages: [{ role: 'user', content: prompt }],
      temperature: 0.7,
      max_tokens: 2000,
      response_format: { type: 'json_object' } // Force JSON response
    };
    
    return this.http.post(this.GROQ_API_URL, body, { headers }).pipe(
      map((response: any) => {
        console.log('✅ AI Response received');
        return this.parseAIResponse(response, cropName);
      }),
      catchError(error => {
        console.error('❌ AI API Error:', error);
        return of(this.getSmartMockData(cropName)); // Fallback to intelligent mock data
      })
    );
  }
  
  private createFarmerPrompt(cropName: string): string {
    return `You are KISAN-GPT, an expert farming advisor for Indian farmers. Provide COMPREHENSIVE farming information for "${cropName}" in this EXACT JSON format:

{
  "crop_name": "${cropName}",
  "best_season": "[Best planting season in India with specific months]",
  "planting_time": "[Exact planting months]",
  "harvest_time": "[Harvest months]",
  "growth_duration": "[Total days from planting to harvest]",
  "soil_type": "[Type of soil needed - be specific]",
  "soil_ph": "[Ideal soil pH range]",
  "water_requirement": "[Total water needed in mm/acre]",
  "irrigation_method": "[Best irrigation method with reasons]",
  "irrigation_schedule": "[How often to irrigate]",
  "fertilizer_schedule": {
    "basal": "[Fertilizer at planting time]",
    "top_dressing": "[Fertilizer during growth stages]",
    "npk_ratio": "[Recommended NPK ratio]",
    "organic_options": "[Organic fertilizer alternatives]"
  },
  "pest_management": {
    "common_pests": "[List common pests]",
    "pesticides": "[Specific pesticide names]",
    "insecticides": "[Specific insecticide names]",
    "organic_control": "[Organic pest control methods]"
  },
  "disease_management": {
    "common_diseases": "[List common diseases]",
    "prevention": "[Disease prevention methods]",
    "treatment": "[Treatment options]"
  },
  "growing_techniques": "[Best cultivation practices]",
  "spacing": "[Plant spacing in cm]",
  "seed_rate": "[Seeds required per acre]",
  "yield_per_acre": "[Expected yield in kg/acre]",
  "current_market_price": "[Current price range in ₹ per kg/quintal]",
  "cost_of_cultivation": "[Total cost per acre in ₹]",
  "expected_profit": "[Expected profit per acre in ₹]",
  "climate_requirements": "[Temperature, humidity, rainfall needs]",
  "sunlight_requirement": "[Sunlight hours needed]",
  "intercropping_options": "[Good companion crops]",
  "pruning_training": "[Pruning and training requirements]",
  "harvesting_technique": "[How to harvest properly]",
  "post_harvest": "[Post-harvest handling]",
  "storage_conditions": "[Ideal storage conditions]",
  "government_schemes": "[Relevant government schemes]",
  "expert_tips": ["Tip 1", "Tip 2", "Tip 3"],
  "risks_challenges": "[Major risks and challenges]",
  "contact_help": "[Where to get help - specific offices]"
}

IMPORTANT INSTRUCTIONS:
1. Provide PRACTICAL, ACTIONABLE advice for Indian farmers
2. Include CURRENT MARKET PRICES (2024)
3. Recommend SPECIFIC pesticide/insecticide BRAND NAMES
4. Include COST & PROFIT calculations
5. Use METRIC units (acres, kg, liters)
6. Mention ORGANIC alternatives
7. Include GOVERNMENT SUPPORT schemes
8. Be SPECIFIC with quantities and timings
9. Make information REGION-SPECIFIC for India`;
  }
  
  private parseAIResponse(response: any, cropName: string): any {
    try {
      const content = response.choices[0]?.message?.content;
      
      if (!content) {
        throw new Error('No content in AI response');
      }
      
      // Clean the JSON response
      const cleanContent = content
        .replace(/```json|```/g, '')
        .replace(/^JSON:\s*/i, '')
        .trim();
      
      console.log('📄 AI Response:', cleanContent);
      
      let data;
      try {
        data = JSON.parse(cleanContent);
      } catch (jsonError) {
        console.warn('JSON parse failed, extracting data from text');
        data = this.extractDataFromText(cleanContent);
      }
      
      // Ensure crop name is set
      data.crop_name = cropName;
      
      return {
        cropName: cropName,
        success: true,
        message: 'Real AI Farming Guide',
        data: data,
        timestamp: new Date().toISOString(),
        source: 'groq-ai'
      };
      
    } catch (error) {
      console.error('🔥 AI Response Parse Error:', error);
      return this.getSmartMockData(cropName);
    }
  }
  
  private extractDataFromText(text: string): any {
    const data: any = {};
    const lines = text.split('\n');
    
    let currentSection = '';
    for (const line of lines) {
      // Check for section headers
      if (line.includes(':')) {
        const [key, ...valueParts] = line.split(':');
        const keyTrimmed = key.trim().toLowerCase().replace(/ /g, '_');
        const value = valueParts.join(':').trim();
        
        if (value && !value.includes('undefined')) {
          // Handle nested objects
          if (keyTrimmed.includes('_')) {
            const parts = keyTrimmed.split('_');
            if (parts.length === 2) {
              if (!data[parts[0]]) data[parts[0]] = {};
              data[parts[0]][parts[1]] = value;
            } else {
              data[keyTrimmed] = value;
            }
          } else {
            data[keyTrimmed] = value;
          }
        }
      }
    }
    
    return data;
  }
  
  private getSmartMockData(cropName: string): any {
    console.log('📚 Using intelligent database for:', cropName);
    
    // Comprehensive database for common crops
    const cropDatabase: any = {
      'rice': {
        crop_name: 'Rice',
        best_season: 'Kharif season (June to November)',
        planting_time: 'June to July',
        harvest_time: 'September to November',
        growth_duration: '90-150 days',
        soil_type: 'Clay loam with good water retention capacity',
        soil_ph: '5.5 to 6.5',
        water_requirement: '1000-2000mm total',
        irrigation_method: 'Flood irrigation or Alternate Wetting and Drying (AWD)',
        irrigation_schedule: 'Maintain 2-5cm standing water',
        fertilizer_schedule: {
          basal: '10-12 tons FYM/acre + 60kg N, 40kg P2O5, 40kg K2O',
          top_dressing: '30kg N at tillering, 30kg N at panicle initiation',
          npk_ratio: '120:60:40 kg/ha',
          organic_options: 'Green manure, compost, vermicompost'
        },
        pest_management: {
          common_pests: 'Stem borer, Brown plant hopper, Rice bug',
          pesticides: 'Carbendazim for blast, Validamycin for sheath blight',
          insecticides: 'Imidacloprid 17.8% SL for BPH, Chlorpyriphos 20% EC for stem borer',
          organic_control: 'Neem oil spray, release Trichogramma wasps, use light traps'
        },
        disease_management: {
          common_diseases: 'Blast, Bacterial blight, Sheath blight, Tungro',
          prevention: 'Use resistant varieties, balanced fertilization, proper spacing',
          treatment: 'Apply appropriate fungicides at first symptom appearance'
        },
        growing_techniques: 'System of Rice Intensification (SRI), transplant 12-15 day old seedlings',
        spacing: '20cm x 20cm for SRI, 15cm x 10cm conventional',
        seed_rate: '20-25 kg/acre for transplanting, 80-100 kg/acre for direct seeding',
        yield_per_acre: '2000-3000 kg (20-30 quintals)',
        current_market_price: '₹2,500 - ₹3,500 per quintal (paddy)',
        cost_of_cultivation: '₹25,000 - ₹35,000 per acre',
        expected_profit: '₹40,000 - ₹60,000 per acre',
        climate_requirements: 'Temperature: 25-35°C, Humidity: 70-80%, Rainfall: 1500-2000mm',
        sunlight_requirement: 'Full sun, 6-8 hours daily',
        intercropping_options: 'Rice + fish, Rice + azolla, Rice + duck',
        pruning_training: 'Not applicable for rice',
        harvesting_technique: 'Harvest when 80% grains turn yellow, moisture content 20-25%',
        post_harvest: 'Thresh immediately, dry to 12-14% moisture, clean and grade',
        storage_conditions: 'Cool, dry place in airtight containers, moisture below 14%',
        government_schemes: 'PM-KISAN, Soil Health Card, MSP for paddy, Pradhan Mantri Krishi Sinchayee Yojana',
        expert_tips: [
          'Use certified seeds of high-yielding varieties',
          'Maintain proper water management',
          'Practice integrated pest management',
          'Get soil testing done before planting'
        ],
        risks_challenges: 'Drought, floods, pest outbreaks, price volatility, climate change',
        contact_help: 'Nearest Krishi Vigyan Kendra, State Agriculture Department, Rice Research Stations'
      },
      'wheat': {
        crop_name: 'Wheat',
        best_season: 'Rabi season (October to March)',
        planting_time: 'November to December',
        harvest_time: 'March to April',
        growth_duration: '110-140 days',
        soil_type: 'Well-drained loamy soil',
        soil_ph: '6.0 to 7.0',
        water_requirement: '400-500mm total',
        irrigation_method: 'Border strip method or sprinkler irrigation',
        irrigation_schedule: '1st: Crown root (21 DAS), 2nd: Tillering (45 DAS), 3rd: Flowering (65 DAS), 4th: Grain filling (85 DAS)',
        fertilizer_schedule: {
          basal: '10 tons FYM + 60kg N, 60kg P2O5, 40kg K2O per acre',
          top_dressing: '40kg N at crown root, 40kg N at flowering',
          npk_ratio: '120:60:40 kg/ha',
          organic_options: 'Farmyard manure, vermicompost, green manure'
        },
        pest_management: {
          common_pests: 'Aphids, Armyworms, Termites, Pink stem borer',
          pesticides: 'Propiconazole 25% EC for rust, Carbendazim 50% WP for loose smut',
          insecticides: 'Imidacloprid 17.8% SL for aphids, Chlorpyriphos 20% EC for termites',
          organic_control: 'Neem cake application, yellow sticky traps, biocontrol agents'
        },
        disease_management: {
          common_diseases: 'Rust, Karnal bunt, Loose smut, Powdery mildew',
          prevention: 'Use disease-free seeds, crop rotation, resistant varieties',
          treatment: 'Apply recommended fungicides at disease appearance'
        },
        growing_techniques: 'Zero tillage, raised bed planting, precision farming',
        spacing: '22.5cm row to row',
        seed_rate: '100-125 kg/acre',
        yield_per_acre: '2000-2500 kg (20-25 quintals)',
        current_market_price: '₹2,200 - ₹2,800 per quintal',
        cost_of_cultivation: '₹20,000 - ₹30,000 per acre',
        expected_profit: '₹30,000 - ₹40,000 per acre',
        climate_requirements: 'Temperature: 15-25°C during growth, cool winters',
        sunlight_requirement: 'Full sun, 6-8 hours daily',
        intercropping_options: 'Wheat + mustard, Wheat + chickpea, Wheat + barley',
        pruning_training: 'Not required for wheat',
        harvesting_technique: 'Harvest when grains hard and moisture 15-20%',
        post_harvest: 'Thresh, clean, dry to 12% moisture',
        storage_conditions: 'Clean, dry bins at 12-15°C, moisture below 12%',
        government_schemes: 'MSP for wheat, PM-KISAN, Soil Health Card, National Food Security Mission',
        expert_tips: [
          'Timely sowing before November 25',
          'Use seed treatment with fungicides',
          'Proper weed management',
          'Moisture conservation practices'
        ],
        risks_challenges: 'Terminal heat stress, frost, rust diseases, market price fluctuations',
        contact_help: 'Wheat Research Stations, Agriculture Universities, KVKs'
      }
      // Add more crops as needed
    };
    
    const lowerCrop = cropName.toLowerCase();
    let data = cropDatabase[lowerCrop];
    
    // If crop not in database, create intelligent response
    if (!data) {
      data = {
        crop_name: cropName,
        best_season: 'Consult local agricultural calendar',
        planting_time: 'Based on regional climate',
        growth_duration: '90-180 days typically',
        soil_type: 'Well-drained fertile soil',
        soil_ph: '6.0-7.0 optimal',
        water_requirement: 'Regular irrigation based on weather',
        irrigation_method: 'Drip irrigation recommended for water efficiency',
        fertilizer_schedule: {
          basal: '10-15 tons FYM/acre + balanced NPK based on soil test',
          top_dressing: 'Apply based on crop growth stage',
          npk_ratio: 'Get soil test done for specific recommendations',
          organic_options: 'Compost, vermicompost, green manure'
        },
        current_market_price: 'Check local mandi or e-NAM portal',
        expert_tips: [
          'Get soil testing done before planting',
          'Use certified quality seeds',
          'Follow integrated pest management',
          'Maintain proper records of inputs and outputs'
        ],
        contact_help: 'Contact nearest Krishi Vigyan Kendra for crop-specific guidance'
      };
    }
    
    return {
      cropName: cropName,
      success: false,
      message: 'Using comprehensive farming database',
      data: data,
      timestamp: new Date().toISOString(),
      source: 'agriculture-database'
    };
  }
}